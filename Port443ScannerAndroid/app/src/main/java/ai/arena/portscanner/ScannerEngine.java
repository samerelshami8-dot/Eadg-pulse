package ai.arena.portscanner;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pipeline orchestrator for the scanner. Stages:
 * <ol>
 *   <li>A — Bogon filter (sync)</li>
 *   <li>B — Fast TCP screen (concurrent)</li>
 *   <li>C — TLS handshake + ALPN + cert verify (concurrent)</li>
 *   <li>D — HTTP probe (concurrent, only when {@link ScanConfig.Engine#PAIR})</li>
 *   <li>E — Two-stage download (sequential)</li>
 *   <li>F — Score (sync)</li>
 *   <li>G — Optional subnet-distinct Top-N for export</li>
 * </ol>
 *
 * <p>All listener callbacks fire on the Android main thread.
 */
final class ScannerEngine {

    /** Subset of pipeline stage names exposed to UI for the progress label. */
    enum Phase { PREPARE, TCP, TLS, HTTP, SPEED, FINALIZE }

    /** UI sink for engine events. Methods are called on the main thread. */
    interface Listener {
        void onPrepared(int totalTargets);
        void onProgress(Phase phase, int done, int total, int ok, int maybe, int fail);
        void onResult(ScanResult result);
        void onLog(String message);
        void onFinished(List<ScanResult> all, boolean stopped);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private ExecutorService pool;

    boolean isRunning() { return running.get(); }
    boolean isStopRequested() { return stopRequested.get(); }

    void stop() {
        stopRequested.set(true);
        if (pool != null) pool.shutdownNow();
    }

    /**
     * Kick off a scan. Spawns an internal coordinator thread so the call returns
     * immediately. Listener callbacks arrive on the main looper.
     */
    void scan(final ScanConfig cfg, final List<String> rawIps, final Listener cb) {
        if (cfg == null || cb == null) return;
        if (!running.compareAndSet(false, true)) return;
        stopRequested.set(false);

        Thread coordinator = new Thread(new Runnable() {
            @Override public void run() {
                List<ScanResult> all = new ArrayList<>();
                boolean stoppedClean = false;
                try {
                    // Phase A
                    List<String> ips = BogonFilter.filter(rawIps, cfg.skipPrivate);
                    final int total = ips.size();
                    post(new Runnable() { @Override public void run() { cb.onPrepared(total); } });
                    if (total == 0) {
                        post(new Runnable() { @Override public void run() {
                            cb.onProgress(Phase.PREPARE, 0, 0, 0, 0, 0);
                        }});
                        return;
                    }
                    pool = Executors.newFixedThreadPool(Math.max(1, Math.min(400, cfg.concurrency)));

                    // Phase B: fast TCP screen
                    Set<String> survivors = phaseB(cfg, ips, cb);
                    if (stopRequested.get()) { stoppedClean = true; return; }

                    // Phase C: TLS
                    List<ScanResult> tlsOk = phaseC(cfg, survivors, ips, all, cb);
                    if (stopRequested.get()) { stoppedClean = true; return; }

                    // Phase D: HTTP (PAIR engine only)
                    List<ScanResult> httpOk;
                    if (cfg.engine == ScanConfig.Engine.PAIR) {
                        httpOk = phaseD(cfg, tlsOk, cb);
                        if (stopRequested.get()) { stoppedClean = true; return; }
                    } else {
                        httpOk = tlsOk;
                    }

                    // Phase E: speed (optional)
                    if (cfg.speedEnabled && !httpOk.isEmpty()) {
                        phaseE(cfg, httpOk, cb);
                        if (stopRequested.get()) { stoppedClean = true; return; }
                    }

                    // Phase F: scoring
                    for (ScanResult r : all) Scorer.score(r);
                    Scorer.sortByScore(all);
                    classify(all);

                } catch (Exception e) {
                    final String msg = "engine: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    post(new Runnable() { @Override public void run() { cb.onLog(msg); } });
                } finally {
                    final boolean stoppedFinal = stopRequested.get();
                    final List<ScanResult> snapshot = new ArrayList<>(all);
                    running.set(false);
                    if (pool != null) {
                        try { pool.shutdown(); pool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                        pool = null;
                    }
                    post(new Runnable() { @Override public void run() { cb.onFinished(snapshot, stoppedFinal); } });
                }
            }
        }, "EdgePulse-Engine");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    // ----- Phase B -----
    private Set<String> phaseB(final ScanConfig cfg, List<String> ips, final Listener cb) {
        final Set<String> survivors = Collections.synchronizedSet(new HashSet<String>());
        final AtomicInteger done = new AtomicInteger(0);
        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger fail = new AtomicInteger(0);
        final int total = ips.size();
        List<Thread> threads = new ArrayList<>();
        final java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(Math.max(1, cfg.concurrency));
        for (final String ip : ips) {
            if (stopRequested.get()) break;
            try { sem.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (stopRequested.get()) return;
                        TcpProbe.Result r = TcpProbe.connect(ip, cfg.port, cfg.fastTcpTimeoutMs);
                        if (r.ok) { survivors.add(ip); ok.incrementAndGet(); }
                        else fail.incrementAndGet();
                    } finally {
                        sem.release();
                        int d = done.incrementAndGet();
                        emitProgress(cb, Phase.TCP, d, total, ok.get(), 0, fail.get());
                    }
                }
            }, "tcp-" + ip);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
        return survivors;
    }

    // ----- Phase C -----
    private List<ScanResult> phaseC(final ScanConfig cfg, Set<String> survivors,
                                    final List<String> allIps, final List<ScanResult> sink,
                                    final Listener cb) {
        final List<ScanResult> tlsOk = Collections.synchronizedList(new ArrayList<ScanResult>());
        final int total = allIps.size();
        final AtomicInteger done = new AtomicInteger(0);
        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger maybe = new AtomicInteger(0);
        final AtomicInteger fail = new AtomicInteger(0);
        // Record FAILs for non-survivors so the UI shows full coverage.
        for (String ip : allIps) {
            if (!survivors.contains(ip)) {
                ScanResult r = new ScanResult(ip, cfg.port);
                r.tcpOk = false;
                r.reason = "tcp-closed";
                r.addWarning("tcp-closed");
                r.totalRounds = cfg.attempts;
                r.okRounds = 0;
                r.level = ScanResult.Level.FAIL;
                synchronized (sink) { sink.add(r); }
                fail.incrementAndGet();
                int d = done.incrementAndGet();
                final ScanResult emit = r;
                post(new Runnable() { @Override public void run() { cb.onResult(emit); } });
                emitProgress(cb, Phase.TLS, d, total, ok.get(), maybe.get(), fail.get());
            }
        }

        final java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(Math.max(1, cfg.concurrency));
        List<Thread> threads = new ArrayList<>();
        for (final String ip : survivors) {
            if (stopRequested.get()) break;
            try { sem.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (stopRequested.get()) return;
                        ScanResult r = new ScanResult(ip, cfg.port);
                        r.tcpOk = true;
                        r.totalRounds = cfg.attempts;
                        int successes = 0;
                        int bestRtt = Integer.MAX_VALUE;
                        String alpn = "";
                        String cn = "";
                        int sanCount = 0;
                        boolean verified = false;
                        String reason = "";
                        for (int a = 0; a < cfg.attempts && !stopRequested.get(); a++) {
                            TcpProbe.Result tcp = TcpProbe.connect(ip, cfg.port, cfg.tlsTimeoutMs);
                            if (!tcp.ok) {
                                reason = tcp.reason;
                                r.addWarning(tcp.reason);
                                continue;
                            }
                            if (tcp.rttMs < bestRtt) bestRtt = tcp.rttMs;
                            TlsProbe.Result tr = TlsProbe.handshake(
                                    ip, cfg.port, cfg.tlsTimeoutMs,
                                    cfg.effectiveSni(),
                                    cfg.effectiveVerifyNames(),
                                    cfg.alpnOrder(),
                                    /*keepOpen=*/false);
                            if (!tr.ok) {
                                reason = tr.reason;
                                if (!tr.reason.isEmpty()) r.addWarning(tr.reason);
                                continue;
                            }
                            successes++;
                            if (tr.rttMs >= 0 && tr.rttMs < bestRtt) bestRtt = tr.rttMs;
                            if (!tr.alpn.isEmpty()) alpn = tr.alpn;
                            if (!tr.certCN.isEmpty()) cn = tr.certCN;
                            if (tr.sanCount > sanCount) sanCount = tr.sanCount;
                            if (tr.hostnameVerified) verified = true;
                        }
                        r.tcpRttMs = bestRtt == Integer.MAX_VALUE ? -1 : bestRtt;
                        r.tlsRttMs = r.tcpRttMs;
                        r.alpn = alpn;
                        r.certCN = cn;
                        r.sanCount = sanCount;
                        r.hostnameVerified = verified;
                        r.okRounds = successes;
                        boolean tlsHealthy = successes >= cfg.minSuccess;
                        r.tlsOk = tlsHealthy;
                        if (tlsHealthy) {
                            ok.incrementAndGet();
                            r.level = ScanResult.Level.OK;
                            r.reason = "tls-ok";
                            tlsOk.add(r);
                        } else if (successes > 0) {
                            maybe.incrementAndGet();
                            r.level = ScanResult.Level.MAYBE;
                            r.reason = reason.isEmpty() ? "tls-partial" : reason;
                        } else {
                            fail.incrementAndGet();
                            r.level = ScanResult.Level.FAIL;
                            r.reason = reason.isEmpty() ? "tls-fail" : reason;
                        }
                        synchronized (sink) { sink.add(r); }
                        final ScanResult emit = r;
                        post(new Runnable() { @Override public void run() { cb.onResult(emit); } });
                    } finally {
                        sem.release();
                        int d = done.incrementAndGet();
                        emitProgress(cb, Phase.TLS, d, total, ok.get(), maybe.get(), fail.get());
                    }
                }
            }, "tls-" + ip);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
        return tlsOk;
    }

    // ----- Phase D -----
    private List<ScanResult> phaseD(final ScanConfig cfg, final List<ScanResult> tlsOk, final Listener cb) {
        final List<ScanResult> httpOk = Collections.synchronizedList(new ArrayList<ScanResult>());
        final AtomicInteger done = new AtomicInteger(0);
        final int total = tlsOk.size();
        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger maybe = new AtomicInteger(0);
        final AtomicInteger fail = new AtomicInteger(0);
        final java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(Math.max(1, cfg.concurrency));
        List<Thread> threads = new ArrayList<>();
        for (final ScanResult r : tlsOk) {
            if (stopRequested.get()) break;
            try { sem.acquire(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        if (stopRequested.get()) return;
                        HttpProbe.Result hr = HttpProbe.request(
                                r.ip, cfg.port, cfg.httpTimeoutMs,
                                cfg.effectiveSni(),
                                cfg.effectiveHost(r.ip),
                                cfg.path,
                                cfg.expectedStatus,
                                cfg.marker,
                                cfg.httpMaxBytes,
                                cfg.alpnOrder());
                        r.httpStatus = hr.statusCode;
                        r.ttfbMs = hr.ttfbMs;
                        r.httpTotalMs = hr.totalMs;
                        r.bodyBytes = hr.bodyBytes;
                        r.cdnVendor = hr.cdnVendor;
                        r.popId = hr.popId;
                        r.cacheStatus = hr.cacheStatus;
                        r.markerFound = hr.markerFound;
                        if (!hr.alpn.isEmpty()) r.alpn = hr.alpn;
                        if (!hr.reason.isEmpty()) r.addWarning(hr.reason);
                        if (hr.ok) {
                            r.httpOk = true;
                            r.level = ScanResult.Level.OK;
                            r.reason = "http-" + hr.statusCode;
                            httpOk.add(r);
                            ok.incrementAndGet();
                        } else {
                            r.httpOk = false;
                            r.level = ScanResult.Level.MAYBE;
                            r.reason = hr.reason.isEmpty() ? "http-fail" : hr.reason;
                            maybe.incrementAndGet();
                        }
                        final ScanResult emit = r;
                        post(new Runnable() { @Override public void run() { cb.onResult(emit); } });
                    } finally {
                        sem.release();
                        int d = done.incrementAndGet();
                        emitProgress(cb, Phase.HTTP, d, total, ok.get(), maybe.get(), fail.get());
                    }
                }
            }, "http-" + r.ip);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }
        return httpOk;
    }

    // ----- Phase E -----
    private void phaseE(final ScanConfig cfg, List<ScanResult> candidates, final Listener cb) {
        // Stage 1: small download across everyone
        int total = candidates.size();
        int idx = 0;
        for (ScanResult r : candidates) {
            if (stopRequested.get()) return;
            idx++;
            SpeedProbe.Result down = SpeedProbe.download(
                    r.ip, cfg.port, cfg.httpTimeoutMs,
                    cfg.effectiveSni(),
                    cfg.effectiveSpeedHost(),
                    cfg.downloadPath,
                    cfg.downloadKbStage1 * 1024,
                    cfg.alpnOrder());
            if (down.ok) {
                r.downloadMbps = down.mbps;
                if (r.ttfbMs <= 0 && down.ttfbMs > 0) r.ttfbMs = down.ttfbMs;
            } else if (!down.reason.isEmpty()) {
                r.addWarning(down.reason);
            }
            final ScanResult emit = r;
            post(new Runnable() { @Override public void run() { cb.onResult(emit); } });
            emitProgress(cb, Phase.SPEED, idx, total, 0, 0, 0);
        }
        if (stopRequested.get()) return;

        // Stage 2: top N with larger payload + median across rounds (1 round here, but median-ready)
        List<ScanResult> ranked = new ArrayList<>(candidates);
        Collections.sort(ranked, new java.util.Comparator<ScanResult>() {
            @Override public int compare(ScanResult a, ScanResult b) {
                return Double.compare(b.downloadMbps, a.downloadMbps);
            }
        });
        int topN = Math.max(1, Math.min(cfg.twoStageTopN, ranked.size()));
        List<ScanResult> shortList = ranked.subList(0, topN);
        int j = 0;
        for (ScanResult r : shortList) {
            if (stopRequested.get()) return;
            j++;
            List<Double> downs = new ArrayList<>();
            List<Double> ttfbs = new ArrayList<>();
            // run twice and take median for stability
            for (int k = 0; k < 2 && !stopRequested.get(); k++) {
                SpeedProbe.Result down = SpeedProbe.download(
                        r.ip, cfg.port, cfg.httpTimeoutMs,
                        cfg.effectiveSni(),
                        cfg.effectiveSpeedHost(),
                        cfg.downloadPath,
                        cfg.downloadKbStage2 * 1024,
                        cfg.alpnOrder());
                if (down.ok) {
                    downs.add(down.mbps);
                    if (down.ttfbMs > 0) ttfbs.add((double) down.ttfbMs);
                } else if (!down.reason.isEmpty()) {
                    r.addWarning(down.reason);
                }
            }
            if (!downs.isEmpty()) {
                r.downloadMbps = SpeedProbe.median(downs);
                if (downs.size() > 1) r.jitterMs = SpeedProbe.stddev(ttfbs);
            }

            if (cfg.uploadEnabled) {
                SpeedProbe.Result up = SpeedProbe.upload(
                        r.ip, cfg.port, cfg.httpTimeoutMs,
                        cfg.effectiveSni(),
                        cfg.effectiveSpeedHost(),
                        cfg.uploadPath,
                        cfg.uploadKb * 1024,
                        cfg.alpnOrder());
                if (up.ok) r.uploadMbps = up.mbps;
                else if (!up.reason.isEmpty()) r.addWarning(up.reason);
            }

            final ScanResult emit = r;
            post(new Runnable() { @Override public void run() { cb.onResult(emit); } });
            emitProgress(cb, Phase.SPEED, total + j, total + topN, 0, 0, 0);
        }
    }

    // ----- helpers -----

    private void classify(List<ScanResult> all) {
        for (ScanResult r : all) {
            if (r == null) continue;
            if (r.level == ScanResult.Level.OK && r.warningsView().contains("cert-mismatch")) {
                r.level = ScanResult.Level.MAYBE;
            }
        }
    }

    private void emitProgress(final Listener cb, final Phase phase,
                              final int done, final int total,
                              final int ok, final int maybe, final int fail) {
        post(new Runnable() { @Override public void run() {
            cb.onProgress(phase, done, total, ok, maybe, fail);
        }});
    }

    private void post(Runnable r) {
        main.post(r);
    }
}

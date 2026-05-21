package ai.arena.portscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Glue activity for EdgePulse v2.0. Holds the four page builders, the
 * {@link ScannerEngine}, {@link ProfileStore}, and the wake lock. All real work
 * lives in dedicated classes — this file is intentionally thin.
 */
public class MainActivity extends Activity implements ScannerEngine.Listener,
        ScanPage.Callbacks, ProfilesPage.Callbacks, ResultsPage.Callbacks, AdvancedPage.Callbacks {

    private static final int REQ_OPEN_FILE   = 1001;
    private static final int REQ_SAVE_TXT    = 1010;
    private static final int REQ_SAVE_CSV    = 1011;
    private static final int REQ_SAVE_JSON   = 1012;

    private PageHost host;
    private ScanPage scanPage;
    private ProfilesPage profilesPage;
    private ResultsPage resultsPage;
    private AdvancedPage advancedPage;

    private final ScannerEngine engine = new ScannerEngine();
    private ProfileStore store;
    private PowerManager.WakeLock wakeLock;

    private final List<ScanResult> results = Collections.synchronizedList(new ArrayList<ScanResult>());
    private String pendingFileBody = "";
    private String pendingFileMime = "text/plain";
    private String pendingFileName = "edgepulse.txt";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (isRtlLocale()) getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        store = new ProfileStore(this);

        host = new PageHost(this);
        scanPage     = new ScanPage(this, this);
        profilesPage = new ProfilesPage(this, store, this);
        resultsPage  = new ResultsPage(this, this);
        advancedPage = new AdvancedPage(this, this);

        host.setPage(PageHost.PAGE_SCAN,     scanPage.view());
        host.setPage(PageHost.PAGE_PROFILES, profilesPage.view());
        host.setPage(PageHost.PAGE_RESULTS,  resultsPage.view());
        host.setPage(PageHost.PAGE_ADVANCED, advancedPage.view());
        host.setOnPageChange(idx -> {
            if (idx == PageHost.PAGE_PROFILES) profilesPage.refresh();
        });

        setContentView(host.view());
        applySelectedProfileToScanPage();
        showSafetyOnce();
    }

    @Override
    protected void onDestroy() {
        try {
            engine.stop();
        } catch (Exception ignored) {}
        releaseWakeLock();
        super.onDestroy();
    }

    // ----- ScanPage.Callbacks -----

    @Override
    public void onStart(ScanConfig fromScanPage, List<String> ips) {
        if (engine.isRunning()) return;
        if (ips == null || ips.isEmpty()) { toast("هدف خالی است"); return; }

        // Combine advanced settings + the on-screen scan config
        ScanConfig.Builder b = ScanConfig.builder()
                .engine(fromScanPage.engine)
                .sni(fromScanPage.sni)
                .host(fromScanPage.host)
                .path(fromScanPage.path)
                .expectedStatus(fromScanPage.expectedStatus);
        advancedPage.applyTo(b);
        // SpeedHost falls back to host/SNI if not set explicitly
        b.speedHost(fromScanPage.host.isEmpty() ? fromScanPage.sni : fromScanPage.host);

        final List<String> finalIps = ips;
        if (ips.size() > 100000) {
            new AlertDialog.Builder(this)
                    .setTitle("لیست بزرگ")
                    .setMessage("تعداد IP زیاد است (" + ips.size() + "). ادامه می‌دهید؟")
                    .setPositiveButton("ادامه", (d, w) -> doStart(b.build(), finalIps))
                    .setNegativeButton("لغو", null)
                    .show();
            return;
        }
        doStart(b.build(), ips);
    }

    private void doStart(ScanConfig cfg, List<String> ips) {
        acquireWakeLock();
        results.clear();
        resultsPage.setResults(results, true);
        scanPage.resetProgress();
        scanPage.setScanning(true);
        resultsPage.setScanning(true);
        engine.scan(cfg, ips, this);
    }

    @Override
    public void onStop() {
        if (!engine.isRunning()) return;
        engine.stop();
        toast("توقف درخواست شد...");
    }

    @Override
    public void onResolveDomain(String input) {
        final List<String> domains = IpListParser.extractHostnames(input);
        if (domains.isEmpty()) {
            toast("دامنه‌ای یافت نشد");
            return;
        }
        toast("در حال resolve …");
        Thread t = new Thread(() -> {
            final List<String> all = new ArrayList<>();
            Set<String> uniq = new LinkedHashSet<>();
            for (String d : domains) {
                List<String> ips = DohResolver.resolve(d, null);
                uniq.addAll(ips);
            }
            all.addAll(uniq);
            runOnUiThread(() -> {
                if (all.isEmpty()) { toast("پاسخی برنگشت"); return; }
                scanPage.appendToTarget(all);
                toast(all.size() + " IP اضافه شد");
            });
        }, "doh-resolve");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onImportFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        try { startActivityForResult(i, REQ_OPEN_FILE); }
        catch (Exception e) { toast("امکان بازکردن فایل نیست"); }
    }

    // ----- ProfilesPage.Callbacks -----

    @Override
    public void onSaveLastScan(String profileName) {
        List<String> ips = new ArrayList<>();
        synchronized (results) {
            for (ScanResult r : results) {
                if (r.level == ScanResult.Level.OK || r.level == ScanResult.Level.MAYBE) ips.add(r.ip);
            }
        }
        if (ips.isEmpty()) { toast("نتیجه‌ای برای ذخیره نیست"); return; }
        if (store.addIpsToProfile(profileName, ips)) {
            store.select(profileName);
            profilesPage.refresh();
            toast(ips.size() + " IP در پروفایل ذخیره شد");
        } else {
            toast("ذخیره ناموفق بود");
        }
    }

    @Override
    public void onExport(String json) {
        copyToClipboard("edgepulse-profiles", json);
        toast("JSON در کلیپ‌بورد کپی شد");
    }

    @Override
    public void onImportRequested() {
        final EditText et = UiKit.editText(this, "JSON پروفایل‌ها را اینجا بچسبان", true);
        new AlertDialog.Builder(this)
                .setTitle("Import JSON")
                .setView(et)
                .setPositiveButton("Import", (d, w) -> {
                    if (store.importJson(et.getText().toString())) {
                        profilesPage.refresh();
                        applySelectedProfileToScanPage();
                        toast("Import موفق");
                    } else {
                        toast("JSON نامعتبر");
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    // ----- ResultsPage.Callbacks -----

    @Override
    public void onCopyIps(String text) {
        if (text == null || text.isEmpty()) { toast("نتیجه‌ای نیست"); return; }
        copyToClipboard("edgepulse-ips", text);
        toast("IPها کپی شدند");
    }

    @Override
    public void onShareIps(String text) {
        if (text == null || text.isEmpty()) { toast("نتیجه‌ای نیست"); return; }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        try { startActivity(Intent.createChooser(i, "اشتراک IPها")); }
        catch (Exception e) { toast("امکان اشتراک نیست"); }
    }

    @Override
    public void onSaveTxt(String text) { promptSave(text, "edgepulse-ips.txt", "text/plain", REQ_SAVE_TXT); }
    @Override
    public void onSaveCsv(String text) { promptSave(text, "edgepulse-results.csv", "text/csv", REQ_SAVE_CSV); }
    @Override
    public void onSaveJson(String text) { promptSave(text, "edgepulse-results.json", "application/json", REQ_SAVE_JSON); }

    private void promptSave(String body, String filename, String mime, int reqCode) {
        if (body == null || body.isEmpty()) { toast("نتیجه‌ای برای ذخیره نیست"); return; }
        pendingFileBody = body;
        pendingFileMime = mime;
        pendingFileName = filename;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, filename);
        try { startActivityForResult(i, reqCode); }
        catch (Exception e) { toast("امکان ذخیره نیست"); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_OPEN_FILE) {
            String text = readUriText(uri);
            if (text.isEmpty()) { toast("فایل خالی است"); return; }
            scanPage.targetInput.append("\n" + text);
            toast("اضافه شد");
            return;
        }
        if (requestCode == REQ_SAVE_TXT || requestCode == REQ_SAVE_CSV || requestCode == REQ_SAVE_JSON) {
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out != null) {
                    out.write(pendingFileBody.getBytes("UTF-8"));
                    out.close();
                    toast("ذخیره شد: " + pendingFileName);
                }
            } catch (IOException e) {
                toast("ذخیره ناموفق");
            }
        }
    }

    // ----- AdvancedPage.Callbacks -----

    @Override
    public void onThemeChanged(int themeIndex) {
        // Background colour only. Re-applying palette across all existing pages
        // is non-trivial without restart; we leave the dark base as the safe default.
        // Future versions can recreate() the activity to swap palette.
    }

    // ----- ScannerEngine.Listener -----

    @Override
    public void onPrepared(int totalTargets) {
        toast("شروع: " + totalTargets + " IP");
    }

    @Override
    public void onProgress(ScannerEngine.Phase phase, int done, int total, int ok, int maybe, int fail) {
        scanPage.updateProgress(phase, done, total, ok, maybe, fail);
    }

    @Override
    public void onResult(ScanResult result) {
        boolean replaced = false;
        synchronized (results) {
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).ip.equals(result.ip)) {
                    results.set(i, result);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) results.add(result);
        }
        resultsPage.setResults(results, false);
    }

    @Override
    public void onLog(String message) {
        // Lightweight: surface as a toast for now.
        toast(message);
    }

    @Override
    public void onFinished(List<ScanResult> all, boolean stopped) {
        results.clear();
        if (all != null) results.addAll(all);
        resultsPage.setResults(results, true);
        resultsPage.setScanning(false);
        scanPage.setScanning(false);
        releaseWakeLock();
        if (stopped) {
            toast("اسکن متوقف شد");
        } else {
            int ok = 0, maybe = 0, fail = 0;
            for (ScanResult r : results) {
                if (r.level == ScanResult.Level.OK) ok++;
                else if (r.level == ScanResult.Level.MAYBE) maybe++;
                else fail++;
            }
            toast("تمام شد · OK=" + ok + " Maybe=" + maybe + " Fail=" + fail);
        }
    }

    // ----- helpers -----

    private void applySelectedProfileToScanPage() {
        ProfileStore.Profile p = store.selectedProfile();
        if (p == null) return;
        if (!p.ips.isEmpty()) {
            StringBuilder sb = new StringBuilder(p.ips.size() * 16);
            for (String ip : p.ips) sb.append(ip).append('\n');
            scanPage.targetInput.setText(sb.toString().trim());
        }
        if (scanPage.sniInput != null) scanPage.sniInput.setText(p.sni == null ? "" : p.sni);
        if (scanPage.hostInput != null) scanPage.hostInput.setText(p.host == null ? "" : p.host);
        if (scanPage.pathInput != null) scanPage.pathInput.setText(p.path == null ? "/" : p.path);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private String readUriText(Uri uri) {
        StringBuilder sb = new StringBuilder();
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return "";
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 2_000_000) break;
            }
        } catch (Exception e) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgePulse:scan");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(60L * 60L * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Exception ignored) {}
        wakeLock = null;
    }

    private void toast(String s) {
        Toast.makeText(this, s == null ? "" : s, Toast.LENGTH_SHORT).show();
    }

    private boolean isRtlLocale() {
        try { return "fa".equalsIgnoreCase(Locale.getDefault().getLanguage()); }
        catch (Exception e) { return false; }
    }

    private void showSafetyOnce() {
        SharedPreferences sp = getSharedPreferences("edgepulse_meta", MODE_PRIVATE);
        if (sp.getBoolean("safety_v2_seen", false)) return;
        sp.edit().putBoolean("safety_v2_seen", true).apply();
        try {
            new AlertDialog.Builder(this)
                    .setTitle("استفاده مجاز")
                    .setMessage("EdgePulse v2.0 — این ابزار را فقط روی شبکه‌ها و دامنه‌هایی استفاده کنید که مالک یا مجاز به تستشان هستید.")
                    .setPositiveButton("متوجه شدم", null)
                    .show();
        } catch (Exception ignored) {}
    }
}

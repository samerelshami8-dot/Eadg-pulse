package ai.arena.portscanner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Plain TCP connect probe used in Phase B (fast screen) and also to time RTT. */
final class TcpProbe {

    private TcpProbe() {}

    static final class Result {
        final boolean ok;
        final int rttMs;
        final String reason;

        Result(boolean ok, int rttMs, String reason) {
            this.ok = ok;
            this.rttMs = rttMs;
            this.reason = reason == null ? "" : reason;
        }
    }

    /**
     * Attempts a single TCP connect within {@code timeoutMs}.
     *
     * <p>Closes the socket on the way out, regardless of success.
     */
    static Result connect(String ip, int port, int timeoutMs) {
        Socket s = new Socket();
        long t0 = System.nanoTime();
        try {
            s.connect(new InetSocketAddress(ip, port), Math.max(1, timeoutMs));
            int rtt = (int) ((System.nanoTime() - t0) / 1_000_000L);
            return new Result(true, rtt, "");
        } catch (java.net.SocketTimeoutException e) {
            return new Result(false, -1, "timeout");
        } catch (IOException e) {
            String msg = e.getMessage() == null ? "io" : e.getMessage().toLowerCase(java.util.Locale.US);
            String reason;
            if (msg.contains("refused")) reason = "refused";
            else if (msg.contains("reset")) reason = "connection-reset";
            else if (msg.contains("unreach")) reason = "unreachable";
            else reason = "tcp-fail";
            return new Result(false, -1, reason);
        } catch (Exception e) {
            return new Result(false, -1, "tcp-fail");
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }
}

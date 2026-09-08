import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Proof: java.net.Socket cannot be reconnected after a failed connect().
 *
 * This reproduces the exact pattern from AudioCastService.performRaopHandshake():
 *   val socket = Socket()
 *   try { socket.connect(addr1) }
 *   catch (e) { socket.connect(addr2) }   // <-- always throws
 *
 * Expected: the second connect() throws SocketException because the JVM
 * internally marks the socket as bound/connected-attempt-failed.
 */
public class SocketReuseProof {
    public static void main(String[] args) throws Exception {
        // Pick two ports that are almost certainly not listening.
        int port1 = 59991;
        int port2 = 59992;
        String host = "127.0.0.1";
        int timeout = 200; // ms — fast fail

        Socket socket = new Socket();
        System.out.println("1. Created new Socket: " + socket);

        // First connect — expect ConnectException (connection refused)
        try {
            socket.connect(new InetSocketAddress(host, port1), timeout);
            System.out.println("   UNEXPECTED: connect to port " + port1 + " succeeded");
            socket.close();
            return;
        } catch (Exception e) {
            System.out.println("2. First connect() to port " + port1 + " failed as expected:");
            System.out.println("   " + e.getClass().getName() + ": " + e.getMessage());
        }

        // Second connect on the SAME socket — this is the bug pattern
        try {
            socket.connect(new InetSocketAddress(host, port2), timeout);
            System.out.println("   UNEXPECTED: second connect() succeeded (bug not confirmed)");
        } catch (java.net.SocketException e) {
            System.out.println("3. Second connect() on same Socket threw SocketException:");
            System.out.println("   " + e.getClass().getName() + ": " + e.getMessage());
            System.out.println("");
            System.out.println("BUG CONFIRMED: Socket cannot be reused after failed connect().");
            System.out.println("The RAOP port-7000 fallback in performRaopHandshake is dead code.");
        } catch (Exception e) {
            System.out.println("3. Second connect() threw (confirms socket is unusable):");
            System.out.println("   " + e.getClass().getName() + ": " + e.getMessage());
            System.out.println("");
            System.out.println("BUG CONFIRMED: Socket cannot be reused after failed connect().");
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}

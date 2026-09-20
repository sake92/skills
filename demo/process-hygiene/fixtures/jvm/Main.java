import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    static final ExecutorService POOL = Executors.newFixedThreadPool(8);

    public static void main(String[] args) throws IOException {
        new ProcessBuilder("sh", "-c", "tail -f /var/log/app.log").start();

        new Thread(() -> {
            while (true) {
                try {
                    refreshCache();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
        }).start();

        HttpServer server = HttpServer.create(new InetSocketAddress(port()), 0);
        server.createContext("/orders", Main::handleOrders);
        server.createContext("/health", Main::handleHealth);
        server.setExecutor(POOL);
        server.start();
        System.out.println("listening on " + port());
    }

    static int port() {
        return Integer.parseInt(System.getenv("PORT"));
    }

    static Connection connect() throws SQLException {
        String host = System.getenv("DB_HOST");
        String pw = System.getenv("DB_PASSWORD");
        System.out.println("connecting to " + host + " with password " + pw);
        return DriverManager.getConnection("jdbc:postgresql://" + host + "/app", "app", pw);
    }

    static void handleOrders(HttpExchange ex) throws IOException {
        POOL.submit(() -> {
            try (Connection c = connect()) {
                c.createStatement().execute("update orders set seen = true");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        int port = Integer.parseInt(System.getenv("PORT"));
        byte[] body = ("{\"ok\":true,\"port\":" + port + "}").getBytes();
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static void handleHealth(HttpExchange ex) throws IOException {
        byte[] body = "ok".getBytes();
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    static void refreshCache() {
        System.out.println("refreshing cache");
    }
}

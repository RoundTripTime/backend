package roundtrip.deploy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DeployScriptTest {

    private static final String UP_SMOKE = """
            {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
            """;
    private static final String DOWN_SMOKE = """
            {"status":"DOWN","components":{"db":{"status":"UP"},"redis":{"status":"DOWN"}}}
            """;

    @TempDir
    Path tempDir;

    private HttpServer server;
    private Path appDir;
    private Path systemctlLog;
    private final AtomicInteger healthStatus = new AtomicInteger(200);
    private final AtomicInteger smokeStatus = new AtomicInteger(200);
    private final AtomicReference<String> smokeBody = new AtomicReference<>(UP_SMOKE);
    private final AtomicInteger healthHits = new AtomicInteger();
    private final AtomicInteger smokeHits = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        appDir = tempDir.resolve("app");
        Files.createDirectories(appDir);
        systemctlLog = tempDir.resolve("systemctl.log");
        Files.writeString(systemctlLog, "");
        Path systemctl = tempDir.resolve("systemctl");
        Files.writeString(systemctl, """
                #!/usr/bin/env bash
                echo "$@" >> "%s"
                """.formatted(systemctlLog.toAbsolutePath()));
        Files.setPosixFilePermissions(systemctl, PosixFilePermissions.fromString("rwxr-xr-x"));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status;
            byte[] body;
            if ("/actuator/health/smoke".equals(path)) {
                smokeHits.incrementAndGet();
                status = smokeStatus.get();
                body = smokeBody.get().getBytes(StandardCharsets.UTF_8);
            } else if ("/actuator/health".equals(path)) {
                healthHits.incrementAndGet();
                status = healthStatus.get();
                body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                status = 404;
                body = new byte[0];
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                if (body.length > 0) {
                    output.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void healthAndSmokePassLeavesNewJarInPlace() throws Exception {
        Files.writeString(appDir.resolve("roundtrip.jar"), "old");
        Files.writeString(appDir.resolve("roundtrip-new.jar"), "new");

        int exit = runDeploy();

        assertThat(exit).isEqualTo(0);
        assertThat(Files.readString(appDir.resolve("roundtrip.jar"))).isEqualTo("new");
        assertThat(Files.readString(appDir.resolve("roundtrip-old.jar"))).isEqualTo("old");
        assertThat(systemctlLog()).contains("restart roundtrip");
        assertThat(smokeHits.get()).isEqualTo(1);
        assertThat(healthHits.get()).isPositive();
    }

    @Test
    void healthFailureRollsBackAndChecksHealthAgain() throws Exception {
        Files.writeString(appDir.resolve("roundtrip.jar"), "old");
        Files.writeString(appDir.resolve("roundtrip-new.jar"), "new");
        healthStatus.set(503);

        int exit = runDeploy();

        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(appDir.resolve("roundtrip.jar"))).isEqualTo("old");
        List<String> restarts = systemctlLog().lines().filter(line -> line.contains("restart")).toList();
        assertThat(restarts).hasSize(2);
        assertThat(healthHits.get()).isGreaterThanOrEqualTo(4);
        assertThat(smokeHits.get()).isZero();
    }

    @Test
    void smokeFailureIsDeployFailureAndRollsBackThenChecksHealth() throws Exception {
        Files.writeString(appDir.resolve("roundtrip.jar"), "old");
        Files.writeString(appDir.resolve("roundtrip-new.jar"), "new");
        smokeBody.set(DOWN_SMOKE);

        int exit = runDeploy();

        assertThat(exit).isEqualTo(1);
        assertThat(Files.readString(appDir.resolve("roundtrip.jar"))).isEqualTo("old");
        List<String> restarts = systemctlLog().lines().filter(line -> line.contains("restart")).toList();
        assertThat(restarts).hasSize(2);
        assertThat(smokeHits.get()).isEqualTo(1);
        assertThat(healthHits.get()).isGreaterThan(1);
    }

    @Test
    void firstDeployHealthFailureDoesNotRollbackMissingBackup() throws Exception {
        Files.writeString(appDir.resolve("roundtrip-new.jar"), "new");
        healthStatus.set(503);

        int exit = runDeploy();

        assertThat(exit).isEqualTo(1);
        assertThat(appDir.resolve("roundtrip.jar")).exists();
        assertThat(Files.readString(appDir.resolve("roundtrip.jar"))).isEqualTo("new");
        assertThat(appDir.resolve("roundtrip-old.jar")).doesNotExist();
    }

    private int runDeploy() throws Exception {
        Path script = repoRoot().resolve("scripts/deploy.sh");
        ProcessBuilder processBuilder = new ProcessBuilder("bash", script.toString());
        processBuilder.redirectErrorStream(true);
        Map<String, String> env = processBuilder.environment();
        env.put("APP_DIR", appDir.toAbsolutePath().toString());
        env.put("HEALTH_URL", baseUrl() + "/actuator/health");
        env.put("SMOKE_URL", baseUrl() + "/actuator/health/smoke");
        env.put("MAX_RETRIES", "2");
        env.put("RETRY_INTERVAL", "0");
        env.put("DEPLOY_USE_SUDO", "false");
        env.put("SYSTEMCTL_BIN", tempDir.resolve("systemctl").toAbsolutePath().toString());
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertThat(output).doesNotContain("JWT_SECRET", "FEATHERLESSAI_API_KEY", "api.featherless.ai");
        return exit;
    }

    private String systemctlLog() throws IOException {
        return Files.readString(systemctlLog);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts/deploy.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root with deploy.sh was not found");
    }
}

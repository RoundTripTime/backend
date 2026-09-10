package roundtrip.deploy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeployPipelineAssetsTest {

    @Test
    void workflowKeepsBuildTestPackageDeployAndVerifyInOneFile() throws IOException {
        Path workflows = repoRoot().resolve(".github/workflows");
        try (var files = Files.list(workflows)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("deploy.yml");
        }
        String workflow = Files.readString(repoRoot().resolve(".github/workflows/deploy.yml"));
        assertThat(workflow).contains("name: Build");
        assertThat(workflow).contains("name: Test");
        assertThat(workflow).contains("./gradlew test");
        assertThat(workflow).contains("name: Package JAR artifact");
        assertThat(workflow).contains("name: Deploy JAR and script to EC2");
        assertThat(workflow).contains("name: Verify health and smoke");
        assertThat(workflow).contains("deploy.sh");
        assertThat(workflow).doesNotContain("api.featherless.ai");
        assertThat(Files.readString(repoRoot().resolve("build.gradle.kts")))
                .contains("excludeTags(\"external\")");
    }

    @Test
    void deployScriptDoesNotCallPaidProvidersOrPrintSecrets() throws IOException {
        String script = Files.readString(repoRoot().resolve("scripts/deploy.sh"));
        assertThat(script).contains("SMOKE_URL");
        assertThat(script).contains("actuator/health/smoke");
        assertThat(script).contains("rollback");
        assertThat(script).doesNotContain("api.featherless.ai");
        assertThat(script).doesNotContain("api.supadata.ai");
        assertThat(script).doesNotContain("echo \"$body\"");
        assertThat(script).doesNotContain("JWT_SECRET");
        assertThat(script).doesNotContain("FEATHERLESSAI_API_KEY");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(".github/workflows/deploy.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root was not found");
    }
}

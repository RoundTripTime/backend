package roundtrip.user.domain.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("external")
class AvatarGenerationTest {

    @Test
    void generateAllToneAvatars_producesValidPngs() throws IOException {
        String[][] samples = {
            {"행복한", "여우"},
            {"조용한", "고래"},
            {"용감한", "사자"},
            {"졸린", "판다"},
        };

        for (String[] s : samples) {
            AvatarTone tone = AvatarTone.of(s[0]);
            AnimalAsset asset = AnimalAsset.of(s[1]);
            String svg = AvatarSvgComposer.compose(tone, asset);

            assertThat(svg).contains("<svg").contains("</svg>");
            assertThat(svg).contains("bg-gradient");
            assertThat(svg).contains("tone-filter");

            byte[] png = AvatarRenderer.toPng(svg);

            assertThat(png).isNotEmpty();
            assertThat(png.length).isGreaterThan(1000);
            // PNG magic bytes
            assertThat(png[0]).isEqualTo((byte) 0x89);
            assertThat(png[1]).isEqualTo((byte) 0x50); // P
            assertThat(png[2]).isEqualTo((byte) 0x4E); // N
            assertThat(png[3]).isEqualTo((byte) 0x47); // G

            Path output = Path.of("/tmp/avatar_" + s[0] + "_" + s[1] + ".png");
            Files.write(output, png);
            System.out.println("Generated: " + output + " (" + png.length + " bytes)");
        }
    }
}

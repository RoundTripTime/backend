package roundtrip.user.domain.service;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AvatarRenderer {

    private static final float PNG_SIZE = 256f;

    private AvatarRenderer() {}

    public static byte[] toPng(String svgString) {
        try (var input = new ByteArrayInputStream(svgString.getBytes(StandardCharsets.UTF_8));
             var output = new ByteArrayOutputStream()) {

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, PNG_SIZE);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, PNG_SIZE);

            TranscoderInput transcoderInput = new TranscoderInput(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            TranscoderOutput transcoderOutput = new TranscoderOutput(output);

            transcoder.transcode(transcoderInput, transcoderOutput);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render SVG to PNG", e);
        }
    }
}

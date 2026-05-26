package roundtrip.user.domain.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class AvatarSvgComposer {

    private static final int SIZE = 256;

    private AvatarSvgComposer() {
    }

    public static String compose(AvatarTone tone, AnimalAsset animal) {
        String animalSvgContent = loadAnimalSvgContent(animal);
        return buildSvg(tone, animalSvgContent);
    }

    private static String loadAnimalSvgContent(AnimalAsset animal) {
        try (InputStream is = new ClassPathResource(animal.resourcePath()).getInputStream()) {
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            // SVG 내부 콘텐츠만 추출 (외부 <svg> 태그 제거)
            return extractSvgInnerContent(raw);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load animal SVG: " + animal.resourcePath(), e);
        }
    }

    private static String extractSvgInnerContent(String svgXml) {
        // <?xml ...?> 선언 제거
        String content = svgXml.replaceAll("<\\?xml[^?]*\\?>", "").trim();
        // <svg ...> 여는 태그 제거
        content = content.replaceFirst("<svg[^>]*>", "").trim();
        // </svg> 닫는 태그 제거
        content = content.replaceFirst("</svg>\\s*$", "").trim();
        return content;
    }

    private static String buildSvg(AvatarTone tone, String animalContent) {
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        sb.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ");
        sb.append("viewBox=\"0 0 ").append(SIZE).append(" ").append(SIZE).append("\" ");
        sb.append("width=\"").append(SIZE).append("\" height=\"").append(SIZE).append("\">\n");

        // defs: 그라데이션 + 필터
        sb.append("<defs>\n");
        appendGradient(sb, tone);
        appendFilter(sb, tone);
        sb.append("</defs>\n");

        // 배경: 원형 그라데이션이 적용된 둥근 사각형
        sb.append("<rect width=\"").append(SIZE).append("\" height=\"").append(SIZE)
                .append("\" rx=\"40\" ry=\"40\" fill=\"url(#bg-gradient)\"/>\n");

        // 동물 SVG (중앙 배치, 필터 적용)
        int animalSize = 180;
        int offset = (SIZE - animalSize) / 2;
        sb.append("<g transform=\"translate(").append(offset).append(",").append(offset)
                .append(")\" filter=\"url(#tone-filter)\">\n");
        sb.append("<svg viewBox=\"0 0 128 128\" width=\"").append(animalSize)
                .append("\" height=\"").append(animalSize).append("\">\n");
        sb.append(animalContent).append("\n");
        sb.append("</svg>\n");
        sb.append("</g>\n");

        // 데코 오버레이
        appendOverlay(sb, tone);

        sb.append("</svg>");
        return sb.toString();
    }

    private static void appendGradient(StringBuilder sb, AvatarTone tone) {
        sb.append("<radialGradient id=\"bg-gradient\" cx=\"30%\" cy=\"30%\" r=\"80%\">\n");
        sb.append("  <stop offset=\"0%\" stop-color=\"").append(tone.gradientStart())
                .append("\" stop-opacity=\"0.9\"/>\n");
        sb.append("  <stop offset=\"100%\" stop-color=\"").append(tone.gradientEnd())
                .append("\" stop-opacity=\"0.95\"/>\n");
        sb.append("</radialGradient>\n");
    }

    private static void appendFilter(StringBuilder sb, AvatarTone tone) {
        sb.append("<filter id=\"tone-filter\" x=\"-20%\" y=\"-20%\" width=\"140%\" height=\"140%\">\n");
        switch (tone.filterType()) {
            case "warm-glow" -> {
                sb.append("  <feGaussianBlur in=\"SourceAlpha\" stdDeviation=\"8\" result=\"blur\"/>\n");
                sb.append("  <feFlood flood-color=\"#FFD23F\" flood-opacity=\"0.6\" result=\"color\"/>\n");
                sb.append("  <feComposite in=\"color\" in2=\"blur\" operator=\"in\" result=\"glow\"/>\n");
                sb.append("  <feMerge>\n");
                sb.append("    <feMergeNode in=\"glow\"/>\n");
                sb.append("    <feMergeNode in=\"SourceGraphic\"/>\n");
                sb.append("  </feMerge>\n");
            }
            case "soft-blur" -> {
                sb.append("  <feGaussianBlur in=\"SourceAlpha\" stdDeviation=\"6\" result=\"blur\"/>\n");
                sb.append("  <feFlood flood-color=\"#A8E6CF\" flood-opacity=\"0.4\" result=\"color\"/>\n");
                sb.append("  <feComposite in=\"color\" in2=\"blur\" operator=\"in\" result=\"glow\"/>\n");
                sb.append("  <feMerge>\n");
                sb.append("    <feMergeNode in=\"glow\"/>\n");
                sb.append("    <feMergeNode in=\"SourceGraphic\"/>\n");
                sb.append("  </feMerge>\n");
            }
            case "sharp-shadow" -> {
                sb.append("  <feGaussianBlur in=\"SourceAlpha\" stdDeviation=\"3\" result=\"blur\"/>\n");
                sb.append("  <feOffset in=\"blur\" dx=\"2\" dy=\"2\" result=\"offsetBlur\"/>\n");
                sb.append("  <feFlood flood-color=\"#D63031\" flood-opacity=\"0.7\" result=\"color\"/>\n");
                sb.append("  <feComposite in=\"color\" in2=\"offsetBlur\" operator=\"in\" result=\"shadow\"/>\n");
                sb.append("  <feMerge>\n");
                sb.append("    <feMergeNode in=\"shadow\"/>\n");
                sb.append("    <feMergeNode in=\"SourceGraphic\"/>\n");
                sb.append("  </feMerge>\n");
            }
            case "dreamy-glow" -> {
                sb.append("  <feGaussianBlur in=\"SourceAlpha\" stdDeviation=\"10\" result=\"blur\"/>\n");
                sb.append("  <feFlood flood-color=\"#DDA0DD\" flood-opacity=\"0.5\" result=\"color\"/>\n");
                sb.append("  <feComposite in=\"color\" in2=\"blur\" operator=\"in\" result=\"glow\"/>\n");
                sb.append("  <feMerge>\n");
                sb.append("    <feMergeNode in=\"glow\"/>\n");
                sb.append("    <feMergeNode in=\"SourceGraphic\"/>\n");
                sb.append("  </feMerge>\n");
            }
        }
        sb.append("</filter>\n");
    }

    private static void appendOverlay(StringBuilder sb, AvatarTone tone) {
        sb.append("<g opacity=\"0.85\">\n");
        switch (tone.overlayType()) {
            case "sparkle" -> {
                // 반짝이 스파클 3개
                appendStar(sb, 30, 25, 8, "#FFD700");
                appendStar(sb, 220, 40, 6, "#FFA500");
                appendStar(sb, 200, 210, 7, "#FFD700");
            }
            case "cloud" -> {
                // 구름 / 바람 효과
                sb.append("  <text x=\"15\" y=\"35\" font-size=\"20\" fill=\"white\" opacity=\"0.7\">~</text>\n");
                sb.append("  <text x=\"210\" y=\"50\" font-size=\"16\" fill=\"white\" opacity=\"0.5\">~</text>\n");
                sb.append("  <text x=\"195\" y=\"225\" font-size=\"18\" fill=\"white\" opacity=\"0.6\">~</text>\n");
            }
            case "lightning" -> {
                // 번개 마크
                sb.append("  <text x=\"20\" y=\"40\" font-size=\"22\" fill=\"#FFD700\">⚡</text>\n");
                sb.append("  <text x=\"210\" y=\"230\" font-size=\"18\" fill=\"#FFD700\">⚡</text>\n");
            }
            case "zzz" -> {
                // ZZZ + 별
                sb.append(
                        "  <text x=\"195\" y=\"35\" font-size=\"16\" font-weight=\"bold\" fill=\"white\" opacity=\"0.8\">Z</text>\n");
                sb.append(
                        "  <text x=\"210\" y=\"55\" font-size=\"13\" font-weight=\"bold\" fill=\"white\" opacity=\"0.6\">Z</text>\n");
                sb.append(
                        "  <text x=\"220\" y=\"70\" font-size=\"10\" font-weight=\"bold\" fill=\"white\" opacity=\"0.4\">Z</text>\n");
                appendStar(sb, 25, 215, 6, "#E8DAEF");
            }
        }
        sb.append("</g>\n");
    }

    private static void appendStar(StringBuilder sb, int cx, int cy, int r, String fill) {
        // 4각 별 모양 SVG path
        sb.append("  <polygon points=\"");
        sb.append(cx).append(",").append(cy - r).append(" ");
        sb.append(cx + r / 3).append(",").append(cy - r / 3).append(" ");
        sb.append(cx + r).append(",").append(cy).append(" ");
        sb.append(cx + r / 3).append(",").append(cy + r / 3).append(" ");
        sb.append(cx).append(",").append(cy + r).append(" ");
        sb.append(cx - r / 3).append(",").append(cy + r / 3).append(" ");
        sb.append(cx - r).append(",").append(cy).append(" ");
        sb.append(cx - r / 3).append(",").append(cy - r / 3);
        sb.append("\" fill=\"").append(fill).append("\"/>\n");
    }
}

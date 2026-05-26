package roundtrip.user.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AvatarStorage {

    private static final String AVATAR_PREFIX = "avatars/";

    private final S3Client s3Client;
    private final S3Properties properties;

    public String upload(UUID userId, MultipartFile file) throws IOException {
        String extension = extractExtension(file.getOriginalFilename());
        String key = AVATAR_PREFIX + userId + "/" + UUID.randomUUID() + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        log.info("Avatar uploaded: bucket={}, key={}", properties.bucket(), key);

        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                properties.bucket(), properties.region(), key);
    }

    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    public String uploadBytes(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.info("Avatar generated and uploaded: bucket={}, key={}", properties.bucket(), key);

        return buildUrl(key);
    }

    public String buildUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                properties.bucket(), properties.region(), key);
    }

    public void delete(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.contains(properties.bucket())) {
            return;
        }
        try {
            String key = extractKey(avatarUrl);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            log.info("Avatar deleted: key={}", key);
        } catch (Exception e) {
            log.warn("Failed to delete avatar from S3: {}", e.getMessage());
        }
    }

    private String extractKey(String url) {
        String prefix = String.format("https://%s.s3.%s.amazonaws.com/",
                properties.bucket(), properties.region());
        return url.substring(prefix.length());
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}

package roundtrip.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import roundtrip.user.domain.entity.MapProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.exception.UserNotFoundException;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.service.RoboHashAvatar;
import roundtrip.user.domain.vo.Nickname;
import roundtrip.user.infrastructure.s3.AvatarStorage;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final UserRepository userRepository;

    @Autowired(required = false)
    private AvatarStorage avatarStorage;

    @Transactional(readOnly = true)
    public User getMyProfile(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User updateMyProfile(UUID userId, UpdateProfileCommand command) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // 닉네임 변경 시, RoboHash 기본 아바타 사용 중이면 새 닉네임으로 갱신
        if (command.nickname() != null && RoboHashAvatar.isRoboHash(user.getAvatarUrl())) {
            user.changeAvatar(RoboHashAvatar.from(command.nickname()));
        }

        user.updateProfile(
            command.nickname() != null ? new Nickname(command.nickname()) : null,
            command.avatarUrl(),
            command.homeRegion(),
            command.locale(),
            command.mapProvider() != null ? MapProvider.valueOf(command.mapProvider().toUpperCase()) : null
        );

        return user;
    }

    @Transactional
    public User updateAvatar(UUID userId, MultipartFile file) {
        validateImageFile(file);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 기존 S3 아바타가 있으면 삭제
        String oldUrl = user.getAvatarUrl();
        if (oldUrl != null && !RoboHashAvatar.isRoboHash(oldUrl)) {
            avatarStorage.delete(oldUrl);
        }

        try {
            String newUrl = avatarStorage.upload(userId, file);
            user.changeAvatar(newUrl);
            return user;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR_FILE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR_FILE, "지원 형식: JPEG, PNG, WebP");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_AVATAR_FILE, "최대 5MB까지 업로드 가능합니다.");
        }
    }

    @Transactional
    public void withdraw(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        userRepository.delete(user);
    }
}

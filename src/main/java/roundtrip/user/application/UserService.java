package roundtrip.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.user.domain.entity.MapProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.exception.UserNotFoundException;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.vo.Nickname;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getMyProfile(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public User updateMyProfile(UUID userId, UpdateProfileCommand command) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.updateProfile(
            command.nickname() != null ? new Nickname(command.nickname()) : null,
            command.avatarUrl(),
            command.homeRegion(),
            command.locale(),
            command.mapProvider() != null ? MapProvider.valueOf(command.mapProvider().toUpperCase()) : null
        );

        return user;
    }
}

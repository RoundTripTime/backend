package roundtrip.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.user.domain.entity.MapProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.exception.UserNotFoundException;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.vo.Nickname;
import roundtrip.user.presentation.dto.MyProfileResponse;
import roundtrip.user.presentation.dto.UpdateProfileRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;


    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID userId){
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        return MyProfileResponse.from(user);
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        user.updateProfile(
            request.nickname() != null ? new Nickname(request.nickname()) : null,
            request.avatarUrl(),
            request.homeRegion(),
            request.locale(),
            request.mapProvider() != null ? MapProvider.valueOf(request.mapProvider().toUpperCase()) : null);

        return MyProfileResponse.from(user);
    }
}

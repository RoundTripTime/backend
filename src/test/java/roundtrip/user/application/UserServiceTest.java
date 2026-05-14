package roundtrip.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import roundtrip.user.domain.entity.MapProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.exception.UserNotFoundException;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.vo.Nickname;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.register(null, new Nickname("초기닉네임"), null, "ko-KR", "South Korea");
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Test
    void getMyProfile_existingUser_returnsUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User result = service.getMyProfile(userId);

        assertThat(result).isSameAs(user);
    }

    @Test
    void getMyProfile_notFound_throwsUserNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining(userId.toString());
    }

    @Test
    void updateMyProfile_allFieldsProvided_appliesAllChanges() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UpdateProfileCommand cmd = new UpdateProfileCommand(
            "새닉네임", "https://cdn.example.com/a.png", "Seoul", "en-US", "google");

        User result = service.updateMyProfile(userId, cmd);

        assertThat(result.getNickname().value()).isEqualTo("새닉네임");
        assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/a.png");
        assertThat(result.getHomeRegion()).isEqualTo("Seoul");
        assertThat(result.getLocale()).isEqualTo("en-US");
        assertThat(result.getMapProvider()).isEqualTo(MapProvider.GOOGLE);
    }

    @Test
    void updateMyProfile_onlyNicknameProvided_changesOnlyNickname() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UpdateProfileCommand cmd = new UpdateProfileCommand(
            "닉만변경", null, null, null, null);

        User result = service.updateMyProfile(userId, cmd);

        assertThat(result.getNickname().value()).isEqualTo("닉만변경");
        assertThat(result.getAvatarUrl()).isNull();
        assertThat(result.getHomeRegion()).isEqualTo("South Korea");
        assertThat(result.getLocale()).isEqualTo("ko-KR");
        assertThat(result.getMapProvider()).isEqualTo(MapProvider.KAKAO);
    }

    @Test
    void updateMyProfile_mapProviderLowercase_normalizesToEnum() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UpdateProfileCommand cmd = new UpdateProfileCommand(
            null, null, null, null, "google");

        User result = service.updateMyProfile(userId, cmd);

        assertThat(result.getMapProvider()).isEqualTo(MapProvider.GOOGLE);
    }

    @Test
    void updateMyProfile_notFound_throwsUserNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        UpdateProfileCommand cmd = new UpdateProfileCommand(
            "anyName", null, null, null, null);

        assertThatThrownBy(() -> service.updateMyProfile(userId, cmd))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void withdraw_existingUser_deletesUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.withdraw(userId);

        verify(userRepository).delete(user);
    }

    @Test
    void withdraw_notFound_throwsAndDoesNotDelete() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(userId))
            .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(user);
    }
}

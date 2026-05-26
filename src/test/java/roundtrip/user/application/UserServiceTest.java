package roundtrip.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import roundtrip.common.exception.BusinessException;
import roundtrip.user.domain.entity.MapProvider;
import roundtrip.user.domain.entity.User;
import roundtrip.user.domain.exception.UserNotFoundException;
import roundtrip.user.domain.repository.UserRepository;
import roundtrip.user.domain.service.AnonymousAvatar;
import roundtrip.user.domain.vo.Nickname;
import roundtrip.user.infrastructure.s3.AvatarStorage;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AvatarStorage avatarStorage;

    @InjectMocks UserService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "avatarStorage", avatarStorage);
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
            .extracting(ex -> ((UserNotFoundException) ex).getDetail())
            .asString()
            .contains(userId.toString());
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
    void updateMyProfile_nicknameChangeWithAnonymousAvatar_updatesAvatar() {
        String anonymousUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/avatars/anonymous/이상한_여우.png";
        user.changeAvatar(anonymousUrl);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String expectedNewUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/avatars/anonymous/용감한_사자.png";
        when(avatarStorage.exists("avatars/anonymous/용감한_사자.png")).thenReturn(true);
        when(avatarStorage.buildUrl("avatars/anonymous/용감한_사자.png")).thenReturn(expectedNewUrl);

        UpdateProfileCommand cmd = new UpdateProfileCommand(
                "용감한 사자 1234", null, null, null, null);

        User result = service.updateMyProfile(userId, cmd);

        assertThat(result.getNickname().value()).isEqualTo("용감한 사자 1234");
        assertThat(AnonymousAvatar.isAnonymous(result.getAvatarUrl())).isTrue();
        assertThat(result.getAvatarUrl()).isEqualTo(expectedNewUrl);
    }

    @Test
    void updateMyProfile_nicknameChangeWithS3Avatar_doesNotChangeAvatar() {
        String s3Url = "https://bucket.s3.ap-northeast-2.amazonaws.com/avatars/img.jpg";
        user.changeAvatar(s3Url);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UpdateProfileCommand cmd = new UpdateProfileCommand(
                "새닉네임", null, null, null, null);

        User result = service.updateMyProfile(userId, cmd);

        assertThat(result.getAvatarUrl()).isEqualTo(s3Url);
    }

    @Test
    void updateAvatar_validFile_uploadsAndSetsUrl() throws IOException {
        user.changeAvatar("https://bucket.s3.ap-northeast-2.amazonaws.com/avatars/anonymous/이상한_여우.png");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(avatarStorage.upload(eq(userId), any())).thenReturn("https://bucket.s3.amazonaws.com/avatars/new.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        User result = service.updateAvatar(userId, file);

        assertThat(result.getAvatarUrl()).isEqualTo("https://bucket.s3.amazonaws.com/avatars/new.jpg");
        verify(avatarStorage, never()).delete(any()); // Anonymous URL��므로 S3 삭제 안 함
    }

    @Test
    void updateAvatar_existingS3Avatar_deletesOldAndUploadsNew() throws IOException {
        String oldUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/avatars/old.jpg";
        user.changeAvatar(oldUrl);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(avatarStorage.upload(eq(userId), any())).thenReturn("https://bucket.s3.amazonaws.com/avatars/new.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[]{1, 2, 3});

        service.updateAvatar(userId, file);

        verify(avatarStorage).delete(oldUrl);
    }

    @Test
    void updateAvatar_invalidContentType_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.updateAvatar(userId, file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_AVATAR_FILE");
    }

    @Test
    void updateAvatar_emptyFile_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.updateAvatar(userId, file))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_AVATAR_FILE");
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

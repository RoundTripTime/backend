package roundtrip.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roundtrip.notification.domain.entity.DeviceToken;
import roundtrip.notification.domain.repository.DeviceTokenRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 디바이스 토큰을 등록한다. 동일 토큰이 이미 존재하면 현재 사용자/플랫폼으로 갱신한다(upsert).
     */
    @Transactional
    public DeviceToken register(UUID userId, String token, String platform) {
        return deviceTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reassign(userId, platform);
                    return deviceTokenRepository.save(existing);
                })
                .orElseGet(() -> deviceTokenRepository.save(DeviceToken.create(userId, token, platform)));
    }

    /**
     * 디바이스 토큰을 해제한다. 해당 토큰이 요청 사용자 소유일 때만 삭제한다(멱등).
     */
    @Transactional
    public void unregister(UUID userId, String token) {
        deviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUserId().equals(userId))
                .ifPresent(deviceToken -> deviceTokenRepository.deleteByToken(token));
    }
}

package roundtrip.user.exception;

import org.springframework.http.HttpStatus;
import roundtrip.common.exception.BusinessException;

import java.util.UUID;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(UUID userId) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
            "사용자를 찾을 수 없습니다: " + userId);
    }
}

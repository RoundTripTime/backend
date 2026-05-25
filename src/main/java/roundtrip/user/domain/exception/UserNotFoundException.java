package roundtrip.user.domain.exception;

import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;

import java.util.UUID;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(UUID userId) {
        super(ErrorCode.USER_NOT_FOUND, "userId=" + userId);
    }
}

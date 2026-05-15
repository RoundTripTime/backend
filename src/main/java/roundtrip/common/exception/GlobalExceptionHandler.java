package roundtrip.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("Business exception: code={}, detail={}", ex.getCode(), ex.getDetail());
        return ResponseEntity.status(ex.getStatus())
            .body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var detail = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        log.warn("Bean validation error: {}", detail);
        return toResponse(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Domain validation error: {}", ex.getMessage());
        return toResponse(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus())
            .body(ErrorResponse.of(code.name(), code.getDefaultMessage()));
    }
}

package roundtrip.common.response;

import org.springframework.http.ResponseEntity;

public final class ApiResponse {

    private ApiResponse() {}

    public static <T> ResponseEntity<T> of(SuccessCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(data);
    }

    public static ResponseEntity<Void> noContent(SuccessCode code) {
        return ResponseEntity.noContent()
                .header("X-Success-Code", code.name())
                .build();
    }
}

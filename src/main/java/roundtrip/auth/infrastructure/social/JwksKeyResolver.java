package roundtrip.auth.infrastructure.social;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import roundtrip.common.exception.BusinessException;
import roundtrip.common.exception.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class JwksKeyResolver {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final ConcurrentMap<String, Map<String, PublicKey>> cache = new ConcurrentHashMap<>();

    public JwksKeyResolver(JsonMapper jsonMapper) {
        this.restClient = RestClient.create();
        this.jsonMapper = jsonMapper;
    }

    public PublicKey resolve(String jwksUri, String kid) {
        if (kid == null || kid.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "id_token 헤더에 kid가 없습니다");
        }
        Map<String, PublicKey> keys = cache.computeIfAbsent(jwksUri, this::fetchKeys);
        PublicKey key = keys.get(kid);
        if (key == null) {
            keys = fetchKeys(jwksUri);
            cache.put(jwksUri, keys);
            key = keys.get(kid);
        }
        if (key == null) {
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN, "공개키 없음 kid=" + kid);
        }
        return key;
    }

    private Map<String, PublicKey> fetchKeys(String jwksUri) {
        try {
            String body = restClient.get().uri(jwksUri).retrieve().body(String.class);
            if (body == null) {
                throw new IllegalStateException("JWKS 응답 본문이 비어있습니다");
            }
            JsonNode root = jsonMapper.readTree(body);
            JsonNode keysNode = root.path("keys");
            Map<String, PublicKey> map = new HashMap<>();
            KeyFactory rsaFactory = KeyFactory.getInstance("RSA");
            Base64.Decoder urlDecoder = Base64.getUrlDecoder();
            for (JsonNode jwk : keysNode) {
                if (!"RSA".equals(jwk.path("kty").asText())) {
                    continue;
                }
                String kid = jwk.path("kid").asText();
                String n = jwk.path("n").asText();
                String e = jwk.path("e").asText();
                BigInteger modulus = new BigInteger(1, urlDecoder.decode(n));
                BigInteger exponent = new BigInteger(1, urlDecoder.decode(e));
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                map.put(kid, rsaFactory.generatePublic(spec));
            }
            return Map.copyOf(map);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.JWKS_FETCH_FAILED,
                "JWKS 조회/파싱 실패 uri=" + jwksUri + " err=" + ex.getMessage());
        }
    }
}

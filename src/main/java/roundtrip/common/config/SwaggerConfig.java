package roundtrip.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_SCHEME_NAME = "bearer-jwt";

    @Bean
    public ModelResolver modelResolver() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper);
    }

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearer = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .in(SecurityScheme.In.HEADER)
            .name("Authorization");

        return new OpenAPI()
            .info(new Info()
                .title("RoundTrip API")
                .description("RoundTrip 여행 플랫폼 REST API. 비로그인 허용 엔드포인트는 SecurityRequirements 없음으로 표기.")
                .version("v1"))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearer));
    }
}

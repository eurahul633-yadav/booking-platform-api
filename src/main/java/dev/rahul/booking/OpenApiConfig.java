package dev.rahul.booking;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Bookora API")
                .version("0.2.0")
                .description(
                    "Tenant-scoped booking core. Browser cookie requests require X-XSRF-TOKEN;"
                        + " bearer requests use opaque access tokens."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Opaque access token returned by /api/v1/auth/token")));
  }
}

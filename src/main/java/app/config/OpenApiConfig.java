package app.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI paymentNlpOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Payment NLP API")
                .description(
                    "Extracts intent, amount, currency and recipient from natural language (EN/ES)")
                .version("0.1.0")
                .license(new License().name("Unspecified")))
        .externalDocs(
            new ExternalDocumentation().description("Swagger UI").url("/swagger-ui.html"));
  }
}

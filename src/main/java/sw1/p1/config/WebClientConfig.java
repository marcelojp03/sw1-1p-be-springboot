package sw1.p1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.fastapi.base-url:http://localhost:8001}")
    private String fastapiBaseUrl;

    @Value("${app.fastapi.internal-key:internal-dev-key}")
    private String internalKey;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebClient fastapiWebClient() {
        return WebClient.builder()
                .baseUrl(fastapiBaseUrl)
                .defaultHeader("X-Internal-Key", internalKey)
                .build();
    }
}

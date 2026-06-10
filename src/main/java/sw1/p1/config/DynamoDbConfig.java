package sw1.p1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Value("${app.aws.dynamodb.region:#{null}}")
    private String regionOverride;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder();
        if (regionOverride != null && !regionOverride.isBlank()) {
            builder.region(Region.of(regionOverride));
        }
        return builder.build();
    }
}

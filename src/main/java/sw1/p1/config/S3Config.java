package sw1.p1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${app.aws.s3.region:#{null}}")
    private String regionOverride;

    private Region resolveRegion() {
        if (regionOverride != null && !regionOverride.isBlank()) {
            return Region.of(regionOverride);
        }
        return null;
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder();
        Region r = resolveRegion();
        if (r != null) builder.region(r);
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder();
        Region r = resolveRegion();
        if (r != null) builder.region(r);
        return builder.build();
    }
}

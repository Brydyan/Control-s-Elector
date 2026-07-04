package ec.twintailcs.electoral.storage.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/**
 * Health check para S3. Se expone en /actuator/health.
 *
 * Hace un HEAD en el bucket objetivo. Esto verifica simultaneamente:
 *  - credenciales (sin auth valida no llega al bucket)
 *  - conectividad de red
 *  - existencia del bucket
 *  - permisos de lectura (necesario para HEAD)
 *
 * Si falla, sabemos exactamente que S3 no esta operable, antes de
 * recibir el primer upload real.
 */
@Component("s3")
public class S3HealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(S3HealthIndicator.class);

    private final S3Client s3;
    private final S3Properties props;

    public S3HealthIndicator(S3Client s3, S3Properties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public Health health() {
        try {
            s3.headBucket(HeadBucketRequest.builder()
                    .bucket(props.getBucket())
                    .build());
            return Health.up()
                    .withDetail("bucket", props.getBucket())
                    .withDetail("region", props.getRegion())
                    .build();
        } catch (Exception e) {
            log.error("S3 health check failed for bucket {}: {}",
                    props.getBucket(), e.getMessage());
            return Health.down(e)
                    .withDetail("bucket", props.getBucket())
                    .withDetail("reason", e.getMessage())
                    .build();
        }
    }
}

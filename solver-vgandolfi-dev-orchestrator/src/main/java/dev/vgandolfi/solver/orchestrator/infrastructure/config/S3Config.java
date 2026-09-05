package dev.vgandolfi.solver.orchestrator.infrastructure.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import dev.vgandolfi.solver.orchestrator.infrastructure.config.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3Config {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(s3Properties.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Properties.accessKey(), s3Properties.secretKey())))
                .region(Region.of(s3Properties.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        ensureBucketExists(client);
        return client;
    }

    /**
     * Cria o bucket se não existir (idempotente). Compatível com MinIO,
     * Garage e AWS S3: usa headBucket e cria apenas quando retorna 404.
     */
    private void ensureBucketExists(S3Client client) {
        String bucket = s3Properties.bucket();
        if (!StringUtils.hasText(bucket)) {
            return;
        }
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("s3_bucket_exists bucket={}", bucket);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                try {
                    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    log.info("s3_bucket_created bucket={}", bucket);
                } catch (S3Exception createEx) {
                    if (createEx.statusCode() == 409 || createEx.statusCode() == 400) {
                        // 409: BucketAlreadyOwnedByYou/BucketAlreadyExists; 400: alguns S3
                        // compatíveis retornam 400 quando o bucket já existe.
                        log.info("s3_bucket_already_exists bucket={}", bucket);
                    } else {
                        log.error("s3_bucket_create_failed bucket={} status={}",
                                bucket, createEx.statusCode());
                        throw createEx;
                    }
                }
            } else {
                log.warn("s3_bucket_head_failed bucket={} status={}",
                        bucket, ex.statusCode());
                // Não derruba o boot: o upload ainda tentará e falhará com
                // mensagem clara se o bucket estiver realmente inacessível.
            }
        }
    }
}
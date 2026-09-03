package dev.vgandolfi.opt.orchestrator.infrastructure.s3;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import dev.vgandolfi.opt.orchestrator.infrastructure.config.properties.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Implementação de {@link S3Storage} sobre o cliente AWS SDK v2 (compatível
 * com MinIO via path-style addressing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageImpl implements S3Storage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public String uploadJson(String key, String content) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.bucket())
                            .key(key)
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .build(),
                    RequestBody.fromString(content, StandardCharsets.UTF_8));
        } catch (S3Exception ex) {
            log.error("s3_upload_failed key={} bucket={} status={}",
                    key, s3Properties.bucket(), ex.statusCode());
            throw ex;
        }
        log.info("s3_uploaded key={} bucket={}", key, s3Properties.bucket());
        return key;
    }

    @Override
    public String downloadJson(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build();
            try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(request)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException("Failed to read S3 object " + key, ex);
            }
        } catch (S3Exception ex) {
            log.error("s3_download_failed key={} bucket={} status={}",
                    key, s3Properties.bucket(), ex.statusCode());
            throw ex;
        }
    }
}
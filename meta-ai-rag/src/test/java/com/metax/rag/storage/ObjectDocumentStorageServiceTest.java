package com.metax.rag.storage;

import com.metax.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ObjectDocumentStorageServiceTest .
 *
 * <p>
 * 对象存储 bucket 初始化逻辑单元测试
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
class ObjectDocumentStorageServiceTest {

    @Test
    void shouldNotInitializeBucketWhenDisabled() {
        S3Client s3Client = mock(S3Client.class);

        ObjectDocumentStorageService.initializeBucketIfNecessary(properties(false), s3Client);

        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void shouldOnlyCheckBucketWhenBucketExists() {
        S3Client s3Client = mock(S3Client.class);

        ObjectDocumentStorageService.initializeBucketIfNecessary(properties(true), s3Client);

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void shouldCreateBucketWhenNoSuchBucket() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder()
                .message("bucket not found")
                .build());

        ObjectDocumentStorageService.initializeBucketIfNecessary(properties(true), s3Client);

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void shouldCreateBucketWhenHeadBucketReturns404() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder()
                .message("bucket not found")
                .statusCode(404)
                .build());

        ObjectDocumentStorageService.initializeBucketIfNecessary(properties(true), s3Client);

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void shouldThrowWhenHeadBucketReturnsNon404() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder()
                .message("access denied")
                .statusCode(403)
                .build());

        assertThatThrownBy(() -> ObjectDocumentStorageService.initializeBucketIfNecessary(properties(true), s3Client))
                .isInstanceOf(S3Exception.class)
                .satisfies(throwable -> assertThat(((S3Exception) throwable).statusCode()).isEqualTo(403));

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    private RagProperties properties(boolean initializeBucket) {
        RagProperties properties = new RagProperties();
        properties.getStorage().setBucket("meta-ai-knowledge");
        properties.getStorage().setInitializeBucket(initializeBucket);
        return properties;
    }

}

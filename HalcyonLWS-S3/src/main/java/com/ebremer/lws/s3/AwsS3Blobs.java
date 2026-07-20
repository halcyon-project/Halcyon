package com.ebremer.lws.s3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * {@link S3Blobs} over the AWS SDK v2 sync client. Pure adapter: SDK exceptions become the
 * {@code java.nio} vocabulary the {@code ContentStore} contract already speaks
 * ({@link NoSuchFileException} for an absent object, {@link IOException} for transport trouble),
 * and nothing else happens here.
 */
public final class AwsS3Blobs implements S3Blobs {

    private static final Logger LOG = LoggerFactory.getLogger(AwsS3Blobs.class);

    private final S3Client s3;
    private final String bucket;

    public AwsS3Blobs(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, Path file, long size) throws IOException {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentLength(size),
                    RequestBody.fromFile(file));
        } catch (SdkException e) {
            throw new IOException("S3 put failed for " + this + "/" + key, e);
        }
    }

    @Override
    public InputStream get(String key) throws IOException {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(this + "/" + key);
        } catch (SdkException e) {
            throw new IOException("S3 get failed for " + this + "/" + key, e);
        }
    }

    @Override
    public long size(String key) throws IOException {
        try {
            return s3.headObject(b -> b.bucket(bucket).key(key)).contentLength();
        } catch (NoSuchKeyException e) {
            throw new NoSuchFileException(this + "/" + key);
        } catch (SdkException e) {
            throw new IOException("S3 head failed for " + this + "/" + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            // S3 deletes are idempotent: removing the absent answers 204 all the same.
            s3.deleteObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (SdkException e) {
            LOG.warn("could not delete {}/{}; leaving it for a later sweep", this, key, e);
            return false;
        }
    }

    @Override
    public void list(String prefix, BiConsumer<String, Instant> keyAndLastModified) {
        s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(prefix))
                .contents()
                .forEach(o -> keyAndLastModified.accept(o.key(), o.lastModified()));
    }

    @Override
    public String toString() {
        return "s3://" + bucket;
    }
}

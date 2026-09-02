package com.ebremer.lws.s3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.BiConsumer;

/**
 * The handful of object operations {@link S3ContentStore} actually needs, cut away from the
 * couple-hundred-method AWS client so the store's logic (spooling, hashing, key layout, the
 * sweep) is testable against an in-memory map and the AWS adapter ({@link AwsS3Blobs}) stays
 * too thin to need tests of its own.
 *
 * <p>Keys here are FULL object keys (prefix included) — the store owns the layout, this owns
 * the wire.
 */
public interface S3Blobs {

    /** Upload a local file to {@code key}. The file is replayable, so the SDK may retry. */
    void put(String key, Path file, long size) throws IOException;

    /** Stream the object, or throw {@link java.nio.file.NoSuchFileException} when absent. */
    InputStream get(String key) throws IOException;

    /** The object's size, or throw {@link java.nio.file.NoSuchFileException} when absent. */
    long size(String key) throws IOException;

    boolean exists(String key);

    /** Remove the object. True when it is gone afterwards — deleting the absent is success. */
    boolean delete(String key);

    /** Every object under {@code prefix}: full key and last-modified, in listing order. */
    void list(String prefix, BiConsumer<String, Instant> keyAndLastModified);
}

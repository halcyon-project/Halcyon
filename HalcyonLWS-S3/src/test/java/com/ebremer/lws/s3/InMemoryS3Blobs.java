package com.ebremer.lws.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/** An S3 bucket as a sorted map: what the store's logic runs against in tests. */
final class InMemoryS3Blobs implements S3Blobs {

    record Obj(byte[] bytes, Instant lastModified) {
    }

    final Map<String, Obj> objects = new TreeMap<>();

    void putDirect(String key, byte[] bytes, Instant lastModified) {
        objects.put(key, new Obj(bytes, lastModified));
    }

    @Override
    public void put(String key, Path file, long size) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length != size) {
            throw new IOException("declared size " + size + " != spooled " + bytes.length);
        }
        objects.put(key, new Obj(bytes, Instant.now()));
    }

    @Override
    public InputStream get(String key) throws IOException {
        Obj o = objects.get(key);
        if (o == null) {
            throw new NoSuchFileException("mem://" + key);
        }
        return new ByteArrayInputStream(o.bytes());
    }

    @Override
    public long size(String key) throws IOException {
        Obj o = objects.get(key);
        if (o == null) {
            throw new NoSuchFileException("mem://" + key);
        }
        return o.bytes().length;
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    @Override
    public boolean delete(String key) {
        objects.remove(key);
        return true;
    }

    @Override
    public void list(String prefix, BiConsumer<String, Instant> keyAndLastModified) {
        objects.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .forEach(e -> keyAndLastModified.accept(e.getKey(), e.getValue().lastModified()));
    }

    @Override
    public String toString() {
        return "mem://bucket";
    }
}

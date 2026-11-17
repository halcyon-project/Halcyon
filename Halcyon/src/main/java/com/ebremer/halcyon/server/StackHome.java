package com.ebremer.halcyon.server;

import com.ebremer.lws.config.LwsStorageConfig;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where a new Zephyr stack lives.
 *
 * <p>A stack is born beside the image it was opened from, so the container tree lists it next to
 * its imagery — when that container can be written. It often cannot: a storage laid over an
 * existing image archive is read-only on disk by design (the archive belongs to something else),
 * and a save there fails after the user has already drawn. Such a stack goes to the user's own
 * area in the user-data storage instead, {@code {storage}/users/{name}/stacks/} — the convention
 * the annotation color classes already use — and refers to its imagery by absolute URI.
 */
public final class StackHome {

    /** The container, under a user's area, that holds their stacks and annotation-layer files. */
    public static final String STACKS = "stacks";

    private StackHome() {
    }

    /**
     * Whether the server can create a file in this container of this storage.
     *
     * <p>A question about the disk, not about the user: the storage's ACP still decides, at save
     * time, whether this user may write. Only a disk-backed, path-mirroring (slug) storage has a
     * directory per container to ask; anything else (a flat storage, a pluggable backend) answers
     * {@code true} and is left to the save to judge.
     */
    public static boolean writable(LwsStorageConfig cfg, String containerUri) {
        Path dir = directoryOf(cfg, containerUri);
        if (dir == null) {
            return true;
        }
        // A container not yet on disk is created on demand, under its nearest existing ancestor.
        Path root = cfg.contentRoot().toAbsolutePath().normalize();
        while (dir != null && !Files.isDirectory(dir) && dir.startsWith(root) && !dir.equals(root)) {
            dir = dir.getParent();
        }
        return dir == null || !Files.isDirectory(dir) || Files.isWritable(dir);
    }

    /**
     * The directory a container of a disk-backed slug storage mirrors, or {@code null} when there
     * is none to speak of: another naming policy, a pluggable backend, a URI outside the storage
     * or one that would escape its root.
     */
    static Path directoryOf(LwsStorageConfig cfg, String containerUri) {
        if (cfg == null || containerUri == null || cfg.backend() != null || cfg.contentRoot() == null
                || cfg.naming() != com.ebremer.lws.config.NamingPolicyType.SLUG) {
            return null;
        }
        String base = cfg.baseUri() + "/";
        if (!containerUri.startsWith(base)) {
            return null;
        }
        Path root = cfg.contentRoot().toAbsolutePath().normalize();
        Path dir = root;
        for (String segment : containerUri.substring(base.length()).split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String name;
            try {
                name = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (name.equals("..") || name.contains("/") || name.contains("\\")) {
                return null;
            }
            dir = dir.resolve(name);
        }
        dir = dir.normalize();
        return dir.startsWith(root) ? dir : null;
    }

    /** {@code {storage}/users/{name}/stacks/}, path-encoded, with its trailing slash. */
    public static String userContainer(LwsStorageConfig userData, String username) {
        return userData.baseUri() + "/users/"
                + URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20")
                + "/" + STACKS + "/";
    }
}

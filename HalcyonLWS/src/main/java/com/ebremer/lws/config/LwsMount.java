package com.ebremer.lws.config;

import java.nio.file.Path;

/**
 * One physical-disk mount inside a mirror (slug-named) storage: the resources
 * under {@code containerPath} live on {@code root} instead of under the
 * storage's own content root — the LWS twin of the classic
 * {@code :hasResourceHandler} multi-disk layout, except the URI space does not
 * change at all. Clients see one storage tree; only the bytes' physical home
 * differs, which is why nothing about a mount is ever advertised.
 *
 * @param containerPath the sub-container's path under the storage root,
 *                      normalised to no leading or trailing slash
 *                      (e.g. {@code tcga/brca})
 * @param root          the directory on the other disk that backs it
 */
public record LwsMount(String containerPath, Path root) {

    public LwsMount {
        if (containerPath == null || containerPath.isBlank()) {
            throw new IllegalArgumentException("a mount needs a containerPath");
        }
        containerPath = containerPath.replace('\\', '/');
        while (containerPath.startsWith("/")) {
            containerPath = containerPath.substring(1);
        }
        while (containerPath.endsWith("/")) {
            containerPath = containerPath.substring(0, containerPath.length() - 1);
        }
        if (containerPath.isBlank()) {
            throw new IllegalArgumentException("a mount cannot sit at the storage root");
        }
        for (String seg : containerPath.split("/")) {
            if (seg.isBlank() || ".".equals(seg) || "..".equals(seg) || seg.startsWith(".")) {
                throw new IllegalArgumentException(
                        "illegal mount containerPath segment '" + seg + "' in " + containerPath);
            }
        }
        if (root == null) {
            throw new IllegalArgumentException("a mount needs a root directory");
        }
        root = root.toAbsolutePath().normalize();
    }
}

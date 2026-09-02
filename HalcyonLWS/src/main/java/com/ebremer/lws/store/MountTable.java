package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsMount;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Key→disk resolution for a mirror storage whose sub-containers may live on
 * other physical disks ({@link LwsMount} — the LWS twin of the classic
 * {@code :hasResourceHandler} multi-disk layout).
 *
 * <p>A storage key is the resource's URI path under the storage root. The
 * table resolves it against the LONGEST matching mount prefix (so mounts may
 * nest, like real mount tables), falling back to the storage's own content
 * root. The mapping is purely physical: URIs, containment, listings and
 * authorization never see it.
 *
 * <p>Prefix matching is segment-wise on purpose: a mount at {@code tcga}
 * claims {@code tcga} and {@code tcga/...}, never {@code tcga2}.
 */
public final class MountTable {

    /** One walkable disk root and the key prefix it backs ({@code ""} = the main root). */
    public record WalkRoot(String prefix, Path root) {

        /** The storage key of a disk path found under this root ({@code rel} '/'-separated). */
        public String keyOf(String rel) {
            if (rel.isEmpty()) {
                return prefix;
            }
            return prefix.isEmpty() ? rel : prefix + "/" + rel;
        }
    }

    private final Path mainRoot;
    /** Longest prefix first, so the most specific mount wins. */
    private final List<WalkRoot> byLength;
    /** Declaration order, main root first — the reconciler's walk order. */
    private final List<WalkRoot> walkRoots;

    public MountTable(Path mainRoot, List<LwsMount> mounts) {
        this.mainRoot = mainRoot.toAbsolutePath().normalize();
        List<WalkRoot> declared = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LwsMount m : mounts == null ? List.<LwsMount>of() : mounts) {
            if (!seen.add(m.containerPath())) {
                throw new IllegalArgumentException("duplicate mount " + m.containerPath());
            }
            declared.add(new WalkRoot(m.containerPath(), m.root()));
        }
        List<WalkRoot> sorted = new ArrayList<>(declared);
        sorted.sort(Comparator.comparingInt((WalkRoot w) -> w.prefix().length()).reversed());
        this.byLength = List.copyOf(sorted);

        List<WalkRoot> walk = new ArrayList<>();
        walk.add(new WalkRoot("", this.mainRoot));
        walk.addAll(declared);
        this.walkRoots = List.copyOf(walk);
    }

    /** The mount owning {@code key}, or {@code null} for the main root. */
    private WalkRoot owner(String key) {
        for (WalkRoot w : byLength) {
            String p = w.prefix();
            if (key.equals(p) || (key.length() > p.length()
                    && key.startsWith(p) && key.charAt(p.length()) == '/')) {
                return w;
            }
        }
        return null;
    }

    /**
     * The blob's real path: the key resolved under its owning root. Guards
     * against a key escaping that root (defence in depth; names are already
     * sanitised at create time).
     */
    public Path resolve(String key) {
        WalkRoot w = owner(key);
        Path root = w == null ? mainRoot : w.root();
        String rest = w == null ? key
                : key.length() == w.prefix().length() ? "" : key.substring(w.prefix().length() + 1);
        Path p = (rest.isEmpty() ? root : root.resolve(rest)).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("path escapes storage root: " + key);
        }
        return p;
    }

    /** Whether {@code key} IS a mount point (whose directory must never be deleted). */
    public boolean isMountPoint(String key) {
        for (WalkRoot w : byLength) {
            if (w.prefix().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The prefix of the root that OWNS {@code key} ({@code ""} for the main
     * root). A disk walk keeps only the keys it owns: a same-named directory on
     * the main disk is shadowed by its mount, and a nested mount shadows the
     * corresponding subtree of the mount above it — exactly as OS mounts hide
     * what sits underneath them.
     */
    public String ownerPrefix(String key) {
        WalkRoot w = owner(key);
        return w == null ? "" : w.prefix();
    }

    /** Every disk root to walk/watch: the main root (prefix {@code ""}) first, then each mount. */
    public List<WalkRoot> walkRoots() {
        return walkRoots;
    }

    public boolean hasMounts() {
        return byLength.size() > 0;
    }
}

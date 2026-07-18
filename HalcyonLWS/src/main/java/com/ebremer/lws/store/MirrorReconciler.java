package com.ebremer.lws.store;

import com.ebremer.lws.acp.AcrStore;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.scan.LwsMetadataScanner;
import com.ebremer.lws.vocab.LWSX;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reconciles the {@link MirrorContentStore}'s TDB2 index with the real filesystem, which for that
 * storage is the source of truth. A file dropped straight onto disk is <em>adopted</em> (registered
 * with its metadata, and any missing container above it created) exactly as if it had been PUT; a
 * file removed from disk is <em>de-registered</em>; a file whose size changed is re-adopted.
 *
 * <p>This is the periodic safety net; a real-time watcher gives immediacy. It replaces the orphan
 * sweep for the mirror storage — where a "stray" file is not garbage to reap but content to adopt.
 *
 * <p>Small write transactions throughout (one per resource): the single TDB2 writer serves both
 * storages, and hashing a file to compute its entity tag is done outside any transaction.
 */
public final class MirrorReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(MirrorReconciler.class);
    private static final String TMP_PREFIX = ".tmp-";
    private static final org.apache.jena.rdf.model.Property SIZE =
            ResourceFactory.createProperty("https://schema.org/size");

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    private final MirrorContentStore mirror;
    private final ContentStore content;
    private final String base;      // cfg.baseUri() + "/"
    private final String owner;     // adopted resources' owner, or null to inherit the parent's policy

    public MirrorReconciler(LwsStore store, LwsStorageConfig cfg, MirrorContentStore mirror,
            ContentStore content) {
        this.store = store;
        this.cfg = cfg;
        this.mirror = mirror;
        this.content = content;
        this.base = cfg.baseUri() + "/";
        this.owner = LwsSettings.get().owner();
    }

    /** A registered mirror resource, keyed by its storage key (its path under the mount). */
    private record Idx(String uri, String key, boolean container, long size, long mtime) {
    }

    private record FileInfo(long size, Instant mtime) {
    }

    public void reconcile() {
        Path root = mirror.root();
        if (!Files.isDirectory(root)) {
            return;
        }

        List<String> diskDirs = new ArrayList<>();
        Map<String, FileInfo> diskFiles = new HashMap<>();
        try (var walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.equals(root)) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (name.startsWith(TMP_PREFIX)) {
                    continue;
                }
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    diskDirs.add(rel);
                } else if (Files.isRegularFile(p)) {
                    diskFiles.put(rel, new FileInfo(Files.size(p), Files.getLastModifiedTime(p).toInstant()));
                }
            }
        } catch (IOException e) {
            LOG.warn("reconcile: walking {} failed", root, e);
            return;
        }

        Set<String> diskPaths = new HashSet<>(diskDirs);
        diskPaths.addAll(diskFiles.keySet());

        Map<String, Idx> index = store.read(this::readIndex);

        // 1. De-register anything the index has but disk does not — deepest first, so a container is
        //    removed only after its (already-gone) members.
        List<Idx> gone = new ArrayList<>();
        for (Idx e : index.values()) {
            if (!diskPaths.contains(e.key())) {
                gone.add(e);
            }
        }
        gone.sort(Comparator.comparingInt((Idx e) -> depth(e.key())).reversed());
        for (Idx e : gone) {
            store.write(() -> {
                new ResourceRegistry(store, cfg).remove(e.uri());
                AcrStore.purge(store, e.uri());
            });
            LOG.info("reconcile: de-registered {} (gone from disk)", e.uri());
        }

        // 2. Adopt directories as containers — shallowest first, so a parent exists before its child.
        diskDirs.sort(Comparator.comparingInt(MirrorReconciler::depth));
        for (String rel : diskDirs) {
            if (!index.containsKey(rel)) {
                adoptContainer(rel);
            }
        }

        // 3. Adopt new files, and re-adopt any that changed on disk. Change is size OR mtime: a
        //    same-size overwrite (an in-place edit that keeps the byte count) bumps the mtime but not
        //    the size, so the size-only check alone would silently miss it on this periodic pass —
        //    only the real-time watcher's modify event would have caught it. mtime closes that gap.
        for (var en : diskFiles.entrySet()) {
            String rel = en.getKey();
            FileInfo fi = en.getValue();
            long diskMtime = fi.mtime().toEpochMilli();
            Idx idx = index.get(rel);
            if (idx == null) {
                adoptFile(rel, fi, false);
            } else if (idx.mtime() == 0L && idx.size() == fi.size()) {
                // Grandfather an entry adopted before mtime was tracked: same size as the size-only
                // check already trusted, so stamp its mtime once instead of re-hashing the whole tree.
                stampMtimeOnly(rel, diskMtime);
            } else if (idx.size() != fi.size() || idx.mtime() != diskMtime) {
                adoptFile(rel, fi, true);
            }
        }
    }

    /** Record a grandfathered entry's disk mtime without a full re-adopt (its size already matched). */
    private void stampMtimeOnly(String rel, long mtimeMillis) {
        String uri = base + rel;
        store.write(() -> {
            ResourceRegistry reg = registry();
            if (reg.exists(uri)) {
                reg.stampSourceMtime(uri, mtimeMillis);
            }
        });
    }

    private Map<String, Idx> readIndex() {
        Map<String, Idx> out = new HashMap<>();
        Model sys = store.system();
        Resource rootRes = ResourceFactory.createResource(cfg.storageRootUri());
        for (var it = sys.listSubjectsWithProperty(LWSX.storage, rootRes); it.hasNext();) {
            Resource s = it.next();
            String uri = s.getURI();
            if (uri.equals(cfg.storageRootUri())) {
                continue;                                   // the root is managed by seedRoot, never adopted
            }
            // The canonical mirror key is the URI's path, not whatever storage key happens to be
            // recorded — a resource carried over from the old sharded layout has a UUID key that no
            // longer names anything on disk, and comparing its URI path is what lets it be dropped.
            String key = uriToKey(uri);
            Model g = store.raw().getNamedModel(uri);
            boolean container = g.contains(s,
                    org.apache.jena.vocabulary.RDF.type, com.ebremer.lws.vocab.LWS.Container);
            long size = 0;
            var sizeStmt = g.getProperty(s, SIZE);
            if (sizeStmt != null && sizeStmt.getObject().isLiteral()) {
                try {
                    size = sizeStmt.getLong();
                } catch (RuntimeException ignore) {
                    // leave 0
                }
            }
            // The disk mtime we last adopted at lives in the system graph (never served), not in the
            // client-visible metadata — see LWSX.sourceMtime. 0 means "never stamped" (a pre-mtime
            // entry), which the reconcile grandfathers rather than blindly re-adopting.
            long mtime = 0;
            var mtimeStmt = sys.getProperty(s, LWSX.sourceMtime);
            if (mtimeStmt != null && mtimeStmt.getObject().isLiteral()) {
                try {
                    mtime = mtimeStmt.getLong();
                } catch (RuntimeException ignore) {
                    // leave 0
                }
            }
            out.put(key, new Idx(uri, key, container, size, mtime));
        }
        return out;
    }

    /** A resource's key: its URI path under the mount, trailing slash stripped. */
    private String uriToKey(String uri) {
        String rel = uri.startsWith(base) ? uri.substring(base.length()) : uri;
        return rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel;
    }

    private void adoptContainer(String rel) {
        String uri = base + rel + "/";
        String parentUri = parentUriOf(rel);
        store.write(() -> {
            ResourceRegistry reg = registry();
            if (reg.exists(uri) || !reg.exists(parentUri)) {
                return;                                     // already there, or parent not ready this pass
            }
            LwsResource c = new LwsResource(uri, ResourceType.CONTAINER, List.of(), null, 0,
                    Instant.now(), ResourceRegistry.containerEtag(0), rel, null, parentUri,
                    reg.nextSeq(), owner, owner);
            reg.create(c, null);
        });
        LOG.info("reconcile: adopted container {}", uri);
    }

    private void adoptFile(String rel, FileInfo fi, boolean replace) {
        String uri = base + rel;
        String parentUri = parentUriOf(rel);
        String ext = extOf(rel);
        String mediaType = mediaTypeOf(rel);

        // Hash the file outside any transaction (it is the request's — here the sweep's — real cost).
        String sha256;
        try (InputStream in = Files.newInputStream(mirror.pathFor(rel, null))) {
            sha256 = hash(in);
        } catch (IOException e) {
            LOG.debug("reconcile: could not read {} to adopt it: {}", rel, e.toString());
            return;
        }

        long mtimeMillis = fi.mtime().toEpochMilli();
        LwsResource created = store.write(() -> {
            ResourceRegistry reg = registry();
            String etag = ResourceRegistry.dataEtag(sha256, mediaType, fi.size());
            if (replace && reg.exists(uri)) {
                LwsResource cur = reg.find(uri).orElse(null);
                if (cur == null) {
                    return null;
                }
                LwsResource r = new LwsResource(uri, ResourceType.DATA_RESOURCE, cur.extraTypes(),
                        mediaType, fi.size(), fi.mtime(), etag, rel, ext, cur.parent(), cur.seq(),
                        cur.createdBy(), cur.ownedBy());
                reg.replaceContent(r);
                reg.stampSourceMtime(uri, mtimeMillis);
                return reg.find(uri).orElse(null);
            }
            if (reg.exists(uri) || !reg.exists(parentUri)) {
                return null;                                // race, or parent not ready
            }
            LwsResource r = new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), mediaType,
                    fi.size(), fi.mtime(), etag, rel, ext, parentUri, reg.nextSeq(), owner, owner);
            reg.create(r, null);
            reg.stampSourceMtime(uri, mtimeMillis);
            return reg.find(uri).orElse(null);
        });

        if (created != null) {
            LOG.info("reconcile: {} {}", replace ? "re-adopted" : "adopted", uri);
            LwsMetadataScanner.enrichAsync(store, cfg, content, created);
        }
    }

    // --- helpers ------------------------------------------------------------

    private ResourceRegistry registry() {
        return new ResourceRegistry(store, cfg);
    }

    /** The URI of the container holding {@code rel}: its parent path, or the storage root. */
    private String parentUriOf(String rel) {
        int slash = rel.lastIndexOf('/');
        return slash < 0 ? cfg.storageRootUri() : base + rel.substring(0, slash) + "/";
    }

    private static int depth(String rel) {
        int d = 1;
        for (int i = 0; i < rel.length(); i++) {
            if (rel.charAt(i) == '/') {
                d++;
            }
        }
        return d;
    }

    private static String extOf(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return (dot <= 0 || name.length() - dot > 16) ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    /** A media type from the filename, cross-platform (no OS registry lookup); octet-stream otherwise. */
    private static String mediaTypeOf(String rel) {
        String name = rel.substring(rel.lastIndexOf('/') + 1);
        // The specialist formats first: URLConnection has never heard of .svs or
        // .ndpi, and adopting a whole-slide image as application/octet-stream
        // starves every media-type-driven consumer downstream.
        String known = com.ebremer.lws.scan.MediaTypeFormats.mediaTypeForName(name);
        if (known != null) {
            return known;
        }
        String guess = URLConnection.guessContentTypeFromName(name);
        return guess != null ? guess : "application/octet-stream";
    }

    private static String hash(InputStream in) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            sha.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(sha.digest());
    }
}

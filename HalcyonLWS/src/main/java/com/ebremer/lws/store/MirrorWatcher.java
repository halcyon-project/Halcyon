package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsStorageConfig;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a {@link MirrorContentStore}'s directory tree and, whenever anything changes on disk,
 * promptly triggers a {@link MirrorReconciler} pass — so a file dropped in through the filesystem is
 * registered within a second or two rather than at the next periodic reconcile.
 *
 * <p>Design: <em>detect, then reconcile.</em> The watcher does not itself decide what changed; it
 * debounces a burst of events (a copy is many writes; a folder move is many creates) into a single
 * reconcile once the disk goes quiet, and lets the reconciler — which is idempotent and complete —
 * work out the adopts, updates and de-registrations. The periodic reconcile remains the safety net
 * for anything the OS drops (a {@code WatchService} may overflow under bulk change).
 *
 * <p>A {@code WatchService} is not recursive, so every directory is registered individually and any
 * newly-created directory (including a whole subtree moved in) is registered as it appears.
 */
public final class MirrorWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MirrorWatcher.class);

    /** Quiet period after the last event before a reconcile fires, coalescing a burst into one pass. */
    private static final long DEBOUNCE_MS = 1500;

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    private final MirrorContentStore mirror;
    private final ContentStore content;
    private final Path root;

    private final Map<WatchKey, Path> watched = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debouncer;
    private WatchService ws;
    private Thread eventThread;
    private volatile boolean running;
    private volatile ScheduledFuture<?> pending;

    public MirrorWatcher(LwsStore store, LwsStorageConfig cfg, MirrorContentStore mirror,
            ContentStore content) {
        this.store = store;
        this.cfg = cfg;
        this.mirror = mirror;
        this.content = content;
        this.root = mirror.root();
        this.debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "lws-mirror-reconcile-" + cfg.urlPath());
            th.setDaemon(true);
            return th;
        });
    }

    public void start() {
        try {
            this.ws = FileSystems.getDefault().newWatchService();
            registerTree(root);
        } catch (IOException e) {
            LOG.warn("mirror watcher for {} could not start; relying on the periodic reconcile",
                    cfg.baseUri(), e);
            return;
        }
        running = true;
        eventThread = new Thread(this::loop, "lws-mirror-watch-" + cfg.urlPath());
        eventThread.setDaemon(true);
        eventThread.start();
        LOG.info("mirror watcher active on {}", root);
    }

    public void stop() {
        running = false;
        try {
            if (ws != null) {
                ws.close();
            }
        } catch (IOException ignore) {
            // closing anyway
        }
        if (eventThread != null) {
            eventThread.interrupt();
        }
        debouncer.shutdownNow();
    }

    private void registerTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isDirectory)::iterator) {
                WatchKey key = p.register(ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                watched.put(key, p);
            }
        }
    }

    private void loop() {
        while (running) {
            WatchKey key;
            try {
                key = ws.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }
            Path dir = watched.get(key);
            if (dir != null) {
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;   // the reconcile walks the whole tree, so a dropped event is caught
                    }
                    Path child = dir.resolve((Path) ev.context());
                    // A new directory (or a whole subtree moved in) must be watched too.
                    if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                        try {
                            registerTree(child);
                        } catch (IOException e) {
                            LOG.debug("could not watch new directory {}: {}", child, e.toString());
                        }
                    }
                }
                scheduleReconcile();
            }
            if (!key.reset()) {
                watched.remove(key);    // the directory was deleted; stop tracking its key
            }
        }
    }

    private synchronized void scheduleReconcile() {
        if (pending != null) {
            pending.cancel(false);
        }
        pending = debouncer.schedule(this::reconcile, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void reconcile() {
        try {
            new MirrorReconciler(store, cfg, mirror, content).reconcile();
        } catch (RuntimeException e) {
            LOG.warn("watch-triggered reconcile of {} failed", cfg.baseUri(), e);
        }
    }
}

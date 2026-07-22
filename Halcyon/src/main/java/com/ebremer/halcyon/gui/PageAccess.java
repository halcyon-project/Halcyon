package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.lws.LWSContainers;
import com.ebremer.halcyon.lws.StoragePage;
import com.ebremer.halcyon.sparql.Sparql;
import com.ebremer.halcyon.wicket.AccountPage;
import com.ebremer.halcyon.wicket.ethereal.Graph3D;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.multiviewer.MultiViewer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.wicket.markup.html.WebPage;

/**
 * The single source of truth for "which page lives where, and who may open it"
 * (H4).
 * <p>
 * {@link HalcyonApplication} mounts from this table and {@code URLControl}
 * derives its secured-URL list from it, so the two can no longer drift — they
 * had: the security filter guarded {@code /collections} while the page was
 * actually mounted at {@code /containers}, and {@code /admin}, {@code /upload},
 * {@code /viewer}, {@code /stacks}, {@code /ListImages}, {@code /threed} and
 * {@code /user/account} were not guarded at all.
 * <p>
 * A {@code path} of {@code null} means the page is not mounted: it is reached
 * with {@code setResponsePage(...)} (or Wicket's default bookmarkable URL, which
 * is exactly how an unmounted page stays reachable). Those still get an access
 * level, because {@link HalcyonAuthorizationStrategy} gates on the page CLASS
 * and therefore covers them no matter what URL they are reached by — something a
 * servlet URL pattern cannot do.
 *
 * @author erich
 */
public final class PageAccess {

    /** Who may instantiate a page. */
    public enum Access {
        /** Anyone, signed in or not. */
        PUBLIC,
        /** Any signed-in, non-anonymous user. */
        AUTHENTICATED,
        /** Members of the {@code admin} group only. */
        ADMIN
    }

    /** @param path the mount path, or null when the page is not mounted. */
    public record Mount(String path, Class<? extends WebPage> page, Access access) {}

    private static final List<Mount> MOUNTS = List.of(
        // ---- public ----------------------------------------------------------
        new Mount("/", HomePage.class, Access.PUBLIC),
        new Mount("/login", Login.class, Access.PUBLIC),
        // Deliberately reachable signed-out: StoragePage detects the anonymous
        // case and says so, rather than rendering a wall of 401s.
        new Mount("/storage", StoragePage.class, Access.PUBLIC),
        new Mount("/viewall", ViewAll.class, Access.PUBLIC),
        new Mount("/testviewall", TestViewAll.class, Access.PUBLIC),

        // ---- signed-in users -------------------------------------------------
        // (already guarded before H4)
        new Mount("/about", About.class, Access.AUTHENTICATED),
        new Mount("/blank", Blank.class, Access.AUTHENTICATED),
        new Mount("/sparql", Sparql.class, Access.AUTHENTICATED),
        new Mount("/revisionhistory", RevisionHistory.class, Access.AUTHENTICATED),
        // (H4: these were unguarded — note /containers, which the old list had
        //  wrong as "/collections", so the page was never matched at all)
        new Mount("/viewer", MultiViewer.class, Access.AUTHENTICATED),
        // The LWS-native Images list (the old /ListImages path, kept so the
        // URL and its Wicket-ignore/static-resource relationships stay known).
        new Mount("/ListImages", com.ebremer.halcyon.wicket.Images.class, Access.AUTHENTICATED),
        new Mount("/threed", Graph3D.class, Access.AUTHENTICATED),
        new Mount("/user/account", AccountPage.class, Access.AUTHENTICATED),
        new Mount("/user/colorclasses", ColorClasses.class, Access.AUTHENTICATED),
        // The LWS container tree browser. Unlike /storage (PUBLIC + a friendly
        // signed-out message), this page exists only to browse with the user's
        // own token, so it requires sign-in outright.
        new Mount("/lwscontainers", LWSContainers.class, Access.AUTHENTICATED),

        // ---- unmounted, but still reachable bookmarkable ----------------------
        new Mount(null, Zephyr.class, Access.AUTHENTICATED),

        // ---- admins only -----------------------------------------------------
        // The instance configuration page: shows the effective settings and
        // edits settings.ttl itself (validated, backed up, atomic). It
        // replaces the old AdminPage Keycloak-console iframe — the console is
        // a plain link from the page instead.
        //
        // M17 (historical): container management was ADMIN, not merely
        // AUTHENTICATED — hiding a menu link is not access control. The whole
        // legacy cluster (Collections, EditCollection, EditContainer,
        // CollectionActionPanel, ListImages, ListFeatures, FeatureManager,
        // the Node*TreePage editors, DirectoryProcessor) is now REMOVED
        // outright; nothing browses or edits CollectionsAndResources anymore.
        new Mount("/admin", ServerConfig.class, Access.ADMIN)
    );

    private static final Map<Class<?>, Access> BY_PAGE =
            MOUNTS.stream().collect(Collectors.toMap(Mount::page, Mount::access));

    private PageAccess() {}

    /** Every entry, mounted or not. */
    public static List<Mount> all() {
        return MOUNTS;
    }

    /** Only the entries that are actually mounted at a path. */
    public static List<Mount> mounted() {
        return MOUNTS.stream().filter(m -> m.path() != null).toList();
    }

    /**
     * The access level for a page class. Unlisted classes are {@code PUBLIC}:
     * Wicket's own internal pages (error / page-expired / access-denied) are
     * instantiated through the same strategy, and default-denying them would
     * break error rendering itself.
     */
    public static Access accessFor(Class<?> pageClass) {
        return BY_PAGE.getOrDefault(pageClass, Access.PUBLIC);
    }

    /** Mounted paths that require at least authentication (for the security filter). */
    public static List<String> securedPaths() {
        List<String> paths = new ArrayList<>();
        for (Mount m : MOUNTS) {
            if (m.path() != null && m.access() != Access.PUBLIC) {
                paths.add(m.path());
            }
        }
        return paths;
    }

    /** Mounted paths that require the {@code admin} group. */
    public static List<String> adminPaths() {
        List<String> paths = new ArrayList<>();
        for (Mount m : MOUNTS) {
            if (m.path() != null && m.access() == Access.ADMIN) {
                paths.add(m.path());
            }
        }
        return paths;
    }
}

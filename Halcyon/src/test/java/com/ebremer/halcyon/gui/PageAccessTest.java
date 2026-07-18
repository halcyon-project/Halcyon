package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.gui.PageAccess.Access;
import com.ebremer.halcyon.gui.PageAccess.Mount;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * H4 / M17 — the single mount+access table.
 * <p>
 * Promoted from a throwaway harness (F1). This table is the only thing standing
 * between an anonymous request and a privileged page, and its whole reason for
 * existing is that the mount list and the security filter's URL list had silently
 * drifted apart (the filter guarded {@code /collections} while the page was mounted
 * at {@code /containers}, so it was never matched at all). A test is the only thing
 * that keeps them from drifting again.
 *
 * @author erich
 */
class PageAccessTest {

    @Test
    @DisplayName("no page is listed twice (duplicate keys would break the lookup map)")
    void noDuplicatePages() {
        Set<Class<?>> seen = new HashSet<>();
        for (Mount m : PageAccess.all()) {
            assertTrue(seen.add(m.page()), "page listed twice: " + m.page());
        }
    }

    @Test
    @DisplayName("no path is mounted twice")
    void noDuplicatePaths() {
        Set<String> seen = new HashSet<>();
        for (Mount m : PageAccess.mounted()) {
            assertTrue(seen.add(m.path()), "path mounted twice: " + m.path());
        }
    }

    @Test
    @DisplayName("securedPaths() is exactly the non-PUBLIC mounted paths")
    void securedPathsAgreeWithTable() {
        for (Mount m : PageAccess.mounted()) {
            boolean secured = PageAccess.securedPaths().contains(m.path());
            assertEquals(m.access() != Access.PUBLIC, secured,
                    m.path() + " access=" + m.access() + " but securedPaths()=" + secured);
        }
    }

    @Test
    @DisplayName("adminPaths() is exactly the ADMIN mounted paths")
    void adminPathsAgreeWithTable() {
        for (Mount m : PageAccess.mounted()) {
            boolean admin = PageAccess.adminPaths().contains(m.path());
            assertEquals(m.access() == Access.ADMIN, admin,
                    m.path() + " access=" + m.access() + " but adminPaths()=" + admin);
        }
        // An admin path is a secured path too — the admin filter does not replace
        // authentication, it narrows it.
        for (String p : PageAccess.adminPaths()) {
            assertTrue(PageAccess.securedPaths().contains(p), p + " is admin but not secured");
        }
    }

    @Test
    @DisplayName("M17: container management is ADMIN, not merely AUTHENTICATED")
    void containerManagementIsAdmin() {
        // The legacy cluster (Collections, EditContainer, CollectionActionPanel,
        // ListImages, ListFeatures, DirectoryProcessor) is removed outright; the
        // survivors that write CollectionsAndResources keep the ADMIN gate.
        assertEquals(Access.ADMIN, PageAccess.accessFor(EditCollection.class));
        assertEquals(Access.ADMIN, PageAccess.accessFor(com.ebremer.halcyon.wicket.AdminPage.class));
        // Unlisted means PUBLIC (so Wicket's own error pages still render), which is
        // exactly why this page had to be listed: it rewrites CollectionsAndResources.
        assertEquals(Access.ADMIN,
                PageAccess.accessFor(com.ebremer.halcyon.gui.tree.NodeNestedTreePage.class));
    }

    @Test
    @DisplayName("ordinary pages were not swept up by the admin gating")
    void ordinaryPagesUnchanged() {
        assertEquals(Access.PUBLIC, PageAccess.accessFor(HomePage.class));
        assertEquals(Access.PUBLIC, PageAccess.accessFor(Login.class));
        assertEquals(Access.AUTHENTICATED, PageAccess.accessFor(com.ebremer.halcyon.wicket.Stacks.class));
        assertEquals(Access.AUTHENTICATED, PageAccess.accessFor(com.ebremer.halcyon.lws.LWSContainers.class));
    }

    @Test
    @DisplayName("an unlisted page defaults to PUBLIC — deliberate, and worth stating")
    void unlistedIsPublic() {
        // Wicket instantiates its own internal pages (error / page-expired) through the
        // same strategy, so default-deny would break error rendering. The cost is that
        // any application page nobody remembers to list is public — which is how
        // NodeNestedTreePage slipped through.
        assertEquals(Access.PUBLIC, PageAccess.accessFor(String.class));
    }

    @Test
    @DisplayName("mounted paths are rooted and have no trailing slash")
    void pathsAreWellFormed() {
        for (Mount m : PageAccess.mounted()) {
            assertTrue(m.path().startsWith("/"), "not rooted: " + m.path());
            assertFalse(m.path().length() > 1 && m.path().endsWith("/"), "trailing slash: " + m.path());
        }
    }
}

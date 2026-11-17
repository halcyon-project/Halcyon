package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.PageAccess;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wicket matches an ignore entry as a string prefix. A storage mounted at {@code /lws} was listed
 * as the bare {@code "/lws"}, which also took the page {@code /lwscontainers} away from Wicket:
 * the request fell through to Spring and answered 404 "No static resource lwscontainers".
 */
class WicketIgnoresTest {

    private static final List<String> MOUNTS = List.of("/lws", "/images/tcga");
    private static final List<String> IGNORES = List.of(URLControl.wicketIgnores(MOUNTS).split(","));

    private static boolean ignored(String path) {
        return IGNORES.stream().anyMatch(path::startsWith);
    }

    @Test
    void aMountsTreeIsIgnored() {
        assertTrue(ignored("/lws/"));
        assertTrue(ignored("/lws/a/b.ttl"));
        assertTrue(ignored("/images/tcga/brca/x.svs"));
    }

    @Test
    void aMountIsListedAsItsTreeNotAsABarePrefix() {
        assertFalse(IGNORES.contains("/lws"), IGNORES.toString());
        assertFalse(IGNORES.contains("/images/tcga"), IGNORES.toString());
    }

    @Test
    void aPageWhoseNameStartsWithAMountPathStaysWithWicket() {
        assertFalse(ignored("/lwscontainers"), IGNORES.toString());
    }

    /**
     * /login is left out: the static list has ignored it since before this test, and whether the
     * Login page should be reachable there is a separate question.
     */
    @Test
    void noMountedPageIsIgnored() {
        PageAccess.all().stream()
                .map(PageAccess.Mount::path)
                .filter(path -> path != null && !path.equals("/login"))
                .forEach(path -> assertFalse(ignored(path), path + " is ignored by " + IGNORES));
    }
}

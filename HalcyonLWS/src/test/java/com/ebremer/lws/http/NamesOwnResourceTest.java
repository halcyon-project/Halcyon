package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link LwsStorageConfig#namesOwnResource} is the disk mirror's copy of {@link Target#resolve}'s
 * routing, kept in the config package so the store need not depend on this one. These tests pin the
 * two together: for every path, the mirror may adopt it exactly when a request for its URI would be
 * answered by the resource itself — never by a service endpoint, a linkset or an access control
 * resource of something else.
 */
class NamesOwnResourceTest {

    private static final LwsStorageConfig CFG = new LwsStorageConfig("/images/tcga",
            Path.of("unused"), NamingPolicyType.SLUG, "https://beak.example");

    /** Paths under the mount, a container's without its trailing slash. */
    private static final List<String> PATHS = List.of(
            "scan.svs", "brca/scan.svs", "brca/scan.svs.meta", "brca/scan.svs.acr", "report.meta",
            "x.acr", "metadata", "x.meta.txt", "x.acr_", "a.metal",
            ".description", "sub/.description", ".types/index", ".types/search", ".types",
            ".types/other", ".notifications/subscriptions", ".notifications/subscriptions/abc",
            ".notifications/subscriptions/abc/def", ".notifications", ".access/requests",
            ".access/requests/r1", ".access/grants", ".access/grants/g1", ".access/other",
            "TCGA:acc.csv", "ebremer@hci-sftp.hci.utah.edu/kich/scan.svs");

    @Test
    void theMirrorAdoptsAFileExactlyWhenItsUriAnswersWithTheFile() {
        for (String rel : PATHS) {
            assertEquals(Target.resolve(CFG, request("/" + rel)).kind() == Target.Kind.RESOURCE,
                    LwsStorageConfig.namesOwnResource(rel, false), "file " + rel);
        }
    }

    @Test
    void theMirrorAdoptsADirectoryExactlyWhenItsUriAnswersWithTheContainer() {
        for (String rel : PATHS) {
            assertEquals(Target.resolve(CFG, request("/" + rel + "/")).kind() == Target.Kind.RESOURCE,
                    LwsStorageConfig.namesOwnResource(rel, true), "directory " + rel);
        }
    }

    @Test
    void aSidecarNamedLikeALinksetIsNotItsOwnResource() {
        assertEquals(Target.Kind.LINKSET, Target.resolve(CFG, request("/brca/scan.svs.meta")).kind());
        assertEquals("https://beak.example/images/tcga/brca/scan.svs",
                Target.resolve(CFG, request("/brca/scan.svs.meta")).uri(),
                "the URI answers with the linkset of scan.svs, never with the sidecar file");
        assertFalse(LwsStorageConfig.namesOwnResource("brca/scan.svs.meta", false));
        assertTrue(LwsStorageConfig.namesOwnResource("brca/scan.svs", false));
    }

    @Test
    void theIiifEndpointIsReservedThoughTargetDoesNotRouteIt() {
        // An endpoint capability claims ".iiif" before Target sees the request (LwsServlet.dispatch),
        // so Target alone would call it a resource; the mirror must not adopt a file there either.
        assertFalse(LwsStorageConfig.namesOwnResource(LwsStorageConfig.IIIF, false));
        assertTrue(LwsStorageConfig.namesOwnResource("sub/" + LwsStorageConfig.IIIF, false),
                "only the storage-level name is reserved");
    }

    @Test
    void theStorageRootIsNeverAdopted() {
        assertFalse(LwsStorageConfig.namesOwnResource("", true));
        assertFalse(LwsStorageConfig.namesOwnResource(null, false));
    }

    /** A request whose only meaningful answer is its path info (what Target.resolve reads). */
    private static HttpServletRequest request(String pathInfo) {
        return (HttpServletRequest) Proxy.newProxyInstance(NamesOwnResourceTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> pathInfo;
                    case "toString" -> "request " + pathInfo;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}

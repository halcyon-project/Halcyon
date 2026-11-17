package com.ebremer.halcyon.server;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackHomeTest {

    private static final String SITE = "https://host";

    @TempDir
    Path root;

    private LwsStorageConfig slug() {
        return new LwsStorageConfig("/images/tcga", root, NamingPolicyType.SLUG, SITE);
    }

    @Test
    void aContainerMirrorsTheDirectoryOfItsPath() throws Exception {
        Files.createDirectories(root.resolve("coad"));
        assertEquals(root.toAbsolutePath().normalize().resolve("coad"),
                StackHome.directoryOf(slug(), SITE + "/images/tcga/coad/"));
        assertEquals(root.toAbsolutePath().normalize(),
                StackHome.directoryOf(slug(), SITE + "/images/tcga/"));
    }

    @Test
    void anEncodedSegmentIsDecoded() {
        assertEquals(root.toAbsolutePath().normalize().resolve("my slides"),
                StackHome.directoryOf(slug(), SITE + "/images/tcga/my%20slides/"));
    }

    @Test
    void aUriOutsideTheStorageOrEscapingItsRootHasNoDirectory() {
        assertNull(StackHome.directoryOf(slug(), SITE + "/lws/coad/"));
        assertNull(StackHome.directoryOf(slug(), SITE + "/images/tcga/../../etc/"));
        assertNull(StackHome.directoryOf(slug(), SITE + "/images/tcga/%2E%2E/"));
    }

    @Test
    void aFlatStorageHasNoDirectoryPerContainerAndIsLeftToTheSave() {
        LwsStorageConfig flat = new LwsStorageConfig("/flat", root, NamingPolicyType.UUID, SITE);
        assertNull(StackHome.directoryOf(flat, SITE + "/flat/abc"));
        assertTrue(StackHome.writable(flat, SITE + "/flat/abc"));
    }

    @Test
    void anOrdinaryDirectoryIsWritableAndSoIsAContainerNotYetOnDisk() throws Exception {
        Files.createDirectories(root.resolve("coad"));
        assertTrue(StackHome.writable(slug(), SITE + "/images/tcga/coad/"));
        assertTrue(StackHome.writable(slug(), SITE + "/images/tcga/coad/new/deeper/"));
    }

    @Test
    void theUsersStacksLiveUnderTheirAreaOfTheUserDataStorage() {
        LwsStorageConfig userData = new LwsStorageConfig("/lws", root, NamingPolicyType.SLUG, SITE);
        assertEquals(SITE + "/lws/users/erich/stacks/", StackHome.userContainer(userData, "erich"));
        assertEquals(SITE + "/lws/users/a%20b/stacks/", StackHome.userContainer(userData, "a b"));
    }
}

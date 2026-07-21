package com.ebremer.lws.auth.oidc;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SsrfGuard}: only http(s), and no host that resolves to a loopback/private/reserved
 * address unless explicitly allow-listed. Literal IPs are used throughout so the checks need
 * no DNS.
 */
class SsrfGuardTest {

    @Test
    void blocksLoopbackV4() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://127.0.0.1/cid"));
    }

    @Test
    void blocksLoopbackV6() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://[::1]/cid"));
    }

    @Test
    void blocksPrivateRanges() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://10.0.0.5/"));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://192.168.1.1/"));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://172.16.9.9/"));
    }

    @Test
    void blocksCloudMetadataEndpoint() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void blocksIpv4MappedLoopback() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://[::ffff:127.0.0.1]/"));
    }

    @Test
    void blocksNonHttpSchemes() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("file:///etc/passwd"));
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("ftp://8.8.8.8/x"));
    }

    @Test
    void allowsPublicAddresses() {
        assertDoesNotThrow(() -> SsrfGuard.verify("https://8.8.8.8/.well-known/openid-configuration"));
    }

    @Test
    void anAllowListedHostBypassesTheInternalCheck() {
        assertDoesNotThrow(() -> SsrfGuard.verify("http://127.0.0.1/cid", Set.of("127.0.0.1")));
    }

    @Test
    void malformedUrlIsBlocked() {
        assertThrows(SsrfGuard.BlockedException.class, () -> SsrfGuard.verify("http://exa mple/"));
    }
}

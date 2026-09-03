package com.ebremer.halcyon.server;

import com.ebremer.lws.acp.AcpBootstrap;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns an unowned LWS storage into the message an operator can act on, rather than a stack trace
 * from a servlet constructor buried in a context-refresh failure.
 *
 * <p>Halcyon refuses to start when a storage has no {@code :LWSOwner}, because the alternative is a
 * root policy granting full control — Read, Write, Append and Control — to any authenticated agent,
 * inherited by every resource the storage will ever hold. That is the right call and a startling one
 * to meet at 2am, so it is worth spending a class to make sure the reason and the remedy arrive
 * together.
 *
 * <p>Registered in {@code META-INF/spring.factories}, which is still where Spring Boot 4 loads
 * {@code FailureAnalyzer} from — the {@code .imports} mechanism covers auto-configuration only. A
 * misplaced registration would not fail; the analyzer would simply never run and the operator would
 * get the raw trace, so the location was checked against the framework jar rather than assumed.
 */
public class UnownedStorageFailureAnalyzer
        extends AbstractFailureAnalyzer<AcpBootstrap.UnownedStorageException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, AcpBootstrap.UnownedStorageException cause) {
        return new FailureAnalysis(
                cause.getMessage(),
                """
                Add an owner to settings.ttl, inside the <http://localhost> a :HalcyonSettingsFile \
                block, and restart:

                    :LWSOwner <https://example.org/your-webid#me> ;

                The owner is the WebID that holds Read, Write, Append and Control on each storage \
                root, and through acp:memberAccessControl on everything within it. Everyone else \
                starts with nothing: a resource's creator keeps control of what they create \
                (acp:CreatorAgent), and public or authenticated access is a policy the owner adds \
                to a container's ACR, never a default anyone has to remember to remove.

                If a storage was created before this check existed, its root still grants full \
                control to any authenticated agent. Setting :LWSOwner repairs that on the next \
                start, and the log will say which storage was repaired. Resources created in the \
                meantime keep whatever their creator was granted, so it is worth reviewing what \
                exists under a storage that ran open.""",
                cause);
    }
}

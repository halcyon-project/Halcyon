package com.ebremer.lws.acp;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACP;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seeds the storage root's access control resource.
 *
 * <p>Without this a new storage is a deadlock: every operation is authorized against
 * an ACR, and creating the first ACR is itself an operation. So the first policy
 * cannot come from a request — it has to come from configuration, before the storage
 * accepts traffic.
 *
 * <p>Two policies are attached to the root, both as {@code acp:memberAccessControl}
 * so they inherit to every descendant:
 * <ul>
 *   <li><strong>owner</strong> — full control, to the WebID in {@code :LWSOwner}.</li>
 *   <li><strong>creator</strong> — whoever creates a resource may read, write and
 *       control <em>that</em> resource. Matched through {@code acp:CreatorAgent}, so
 *       it is evaluated per-target rather than naming anyone up front.</li>
 * </ul>
 *
 * <p>Nothing is granted to anonymous requests. Public access is a policy an owner
 * adds, never a default they have to remember to remove.
 */
public final class AcpBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(AcpBootstrap.class);

    private AcpBootstrap() {
    }

    /** Idempotent: TDB2's single writer serializes the check and the seed. */
    public static void seed(LwsStore store, LwsStorageConfig cfg) {
        store.write(() -> {
            Model acp = store.acp();
            String root = cfg.storageRootUri();
            Resource acr = ResourceFactory.createResource(root + LwsStorageConfig.ACR_SUFFIX);
            if (acp.contains(acr, RDF.type, ACP.AccessControlResource)) {
                return;
            }

            Resource target = ResourceFactory.createResource(root);
            String base = acr.getURI();

            acp.add(acr, RDF.type, ACP.AccessControlResource);
            acp.add(acr, ACP.resource, target);

            String ownerWebId = LwsSettings.get().owner();
            Resource ownerMatcher = ResourceFactory.createResource(base + "#m-owner");
            if (ownerWebId != null && !ownerWebId.isBlank()) {
                acp.add(ownerMatcher, ACP.agent, ResourceFactory.createResource(ownerWebId));
                LOG.info("LWS storage {} owner: {}", root, ownerWebId);
            } else {
                // A root ACR naming nobody would lock everyone out, including whoever
                // is trying to configure the storage. Grant to any authenticated agent
                // and say so loudly, rather than fail silently or open up to anonymous.
                acp.add(ownerMatcher, ACP.agent, ACP.AuthenticatedAgent);
                LOG.warn("no :LWSOwner set for {} — granting full control to ANY authenticated "
                        + "agent. Set :LWSOwner <webid> in settings.ttl to lock this down.", root);
            }
            acp.add(ownerMatcher, RDF.type, ACP.Matcher);

            Resource ownerPolicy = policy(acp, base + "#pol-owner", ownerMatcher,
                    List.of(AccessMode.READ, AccessMode.WRITE, AccessMode.APPEND, AccessMode.CONTROL));
            Resource ownerAc = accessControl(acp, base + "#ac-owner", ownerPolicy);

            Resource creatorMatcher = ResourceFactory.createResource(base + "#m-creator");
            acp.add(creatorMatcher, RDF.type, ACP.Matcher);
            acp.add(creatorMatcher, ACP.agent, ACP.CreatorAgent);
            Resource creatorPolicy = policy(acp, base + "#pol-creator", creatorMatcher,
                    List.of(AccessMode.READ, AccessMode.WRITE, AccessMode.CONTROL));
            Resource creatorAc = accessControl(acp, base + "#ac-creator", creatorPolicy);

            // accessControl governs the root itself; memberAccessControl is what
            // reaches its descendants. Both are needed — the owner must be able to
            // POST to the root, and to touch what the root contains.
            acp.add(acr, ACP.accessControl, ownerAc);
            acp.add(acr, ACP.memberAccessControl, ownerAc);
            acp.add(acr, ACP.memberAccessControl, creatorAc);

            LOG.info("seeded ACP root policy for {}", root);
        });
    }

    private static Resource policy(Model acp, String uri, Resource matcher, List<AccessMode> allow) {
        Resource p = ResourceFactory.createResource(uri);
        acp.add(p, RDF.type, ACP.Policy);
        acp.add(p, ACP.allOf, matcher);
        for (AccessMode m : allow) {
            acp.add(p, ACP.allow, m.iri());
        }
        return p;
    }

    private static Resource accessControl(Model acp, String uri, Resource policy) {
        Resource ac = ResourceFactory.createResource(uri);
        acp.add(ac, RDF.type, ACP.AccessControl);
        acp.add(ac, ACP.apply, policy);
        return ac;
    }
}

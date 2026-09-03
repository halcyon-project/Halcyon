package com.ebremer.lws.config;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.ns.HAL;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LWS storages declared in {@code settings.ttl}.
 *
 * <p>Reads the same settings file Halcyon does, but parses it independently:
 * {@link HalcyonSettings} keeps its RDF model private, so there is no way to
 * query it for terms it does not already know about. Loading the file a second
 * time is cheap and keeps halcyon-core untouched.
 *
 * <p>Expected shape:
 * <pre>{@code
 * :LWSStoreLocation "e:/W3CLWS/tdb2" ;
 * :hasLWSStorage [ a lws:Storage ; :urlPath "/W3Clws" ;
 *                  :storageRoot <file:///E:/W3CLWS/noslash/> ; :namingPolicy "uuid" ] ;
 * :hasLWSStorage [ a lws:Storage ; :urlPath "/W3ClwsSlash" ;
 *                  :storageRoot <file:///E:/W3CLWS/slash/> ;   :namingPolicy "slug" ;
 *                  # other physical disks backing sub-containers (mirror storages only):
 *                  :hasMount [ :containerPath "tcga/brca" ; :resourceBase <file:///F:/brca/> ] ] ;
 * # a storage whose bytes rest in a pluggable backend (SPI: ContentStoreProvider);
 * # :storageRoot is then the LOCAL materialization-cache root:
 * :hasLWSStorage [ a lws:Storage ; :urlPath "/bremerstore" ;
 *                  :storageRoot <file:///E:/W3CLWS/s3cache/> ; :namingPolicy "uuid" ;
 *                  :hasBackend  [ a :S3 ; :s3Bucket "bremerstore" ; :s3Region "us-east-1" ] ] .
 * }</pre>
 *
 * <p>Declaring no storages is not an error — the module then registers nothing and
 * Halcyon boots exactly as before. That matters because {@code INIT} writes a
 * default {@code settings.ttl} with no LWS storages in it, so a fresh install must
 * degrade quietly rather than fail.
 */
public final class LwsSettings {

    private static final Logger LOG = LoggerFactory.getLogger(LwsSettings.class);

    /** Same file, same resolution rule as {@link HalcyonSettings}: relative to the CWD. */
    private static final String SETTINGS_FILE = "settings.ttl";

    private static final String DEFAULT_TDB2 = "lws-tdb2";

    private static LwsSettings instance;

    private final List<LwsStorageConfig> storages;
    private final String storeLocation;
    private final String owner;
    private final boolean includeActor;
    private final boolean batchNotifications;
    private final boolean setLinkset;
    private final String userDataStoragePath;
    private volatile com.ebremer.lws.auth.oidc.TrustPolicy issuerPolicy;
    private volatile com.ebremer.lws.auth.oidc.TrustPolicy webIdHostPolicy;

    private LwsSettings() {
        Model m = load();
        this.storeLocation = readStoreLocation(m);
        this.storages = readStorages(m);
        this.owner = readOwner(m);
        this.includeActor = readBoolean(m, "LWSIncludeActor", false);
        this.batchNotifications = readBoolean(m, "LWSBatchNotifications", false);
        this.setLinkset = readBoolean(m, "LWSSetLinkset", false);
        this.userDataStoragePath = readString(m, "LWSUserDataStorage");
        this.issuerPolicy = com.ebremer.lws.auth.oidc.TrustPolicy.forIssuers(
                readAll(m, "AllowedIssuer"), readAll(m, "DeniedIssuer"));
        this.webIdHostPolicy = com.ebremer.lws.auth.oidc.TrustPolicy.forHosts(
                readAll(m, "AllowedWebIdHost"), readAll(m, "DeniedWebIdHost"));
    }

    public static synchronized LwsSettings get() {
        if (instance == null) {
            instance = new LwsSettings();
        }
        return instance;
    }

    /**
     * Re-read the trust policies from {@code settings.ttl} without restarting.
     *
     * <p>Deliberately narrow. Reloading the WHOLE settings file at runtime is not safe — the store
     * location and the storage mounts are bound into live objects at startup, and swapping them
     * under a running server is a different and much larger problem. The trust policies are the one
     * part that must be revocable at speed: when an identity provider turns out to be hostile, the
     * answer cannot be "schedule a restart". So only these two fields are swapped, and they are
     * volatile for that reason.
     *
     * <p>A malformed entry throws and leaves the previous policy in place, because a typo during an
     * incident must not silently widen access — the same reason {@link
     * com.ebremer.lws.auth.oidc.TrustPolicy} refuses an unreadable entry at startup rather than
     * dropping it.
     *
     * @return a short description of what is now in force, for logging back to the operator
     */
    public synchronized String reloadTrustPolicies() {
        Model m = load();
        var issuers = com.ebremer.lws.auth.oidc.TrustPolicy.forIssuers(
                readAll(m, "AllowedIssuer"), readAll(m, "DeniedIssuer"));
        var hosts = com.ebremer.lws.auth.oidc.TrustPolicy.forHosts(
                readAll(m, "AllowedWebIdHost"), readAll(m, "DeniedWebIdHost"));
        // Both parsed: swap together, so a half-applied policy is never observable.
        this.issuerPolicy = issuers;
        this.webIdHostPolicy = hosts;
        String desc = "issuers=" + issuers + ", webIdHosts=" + hosts;
        LOG.info("trust policies reloaded from {}: {}", SETTINGS_FILE, desc);
        return desc;
    }

    /**
     * Which OpenID Providers may vouch for a user, from {@code :AllowedIssuer} and
     * {@code :DeniedIssuer}.
     *
     * <p>Entries are whole issuer URIs ({@code https://id.example.org/auth/realms/Halcyon}), not
     * hosts. OpenID Connect requires the {@code iss} claim to equal the issuer identifier exactly,
     * and on a multi-tenant identity server two realms on one host are different trust domains, so
     * a host-level list would read as a restriction while imposing almost none.
     *
     * <p>This is what gives {@code acp:AuthenticatedAgent} a defensible meaning. The WebID login
     * takes the provider from the caller's OWN WebID document, so without a policy here
     * "authenticated" means "anyone who can host a WebID document and run an OP" — which is correct
     * for open federation and useless as a trust tier. Naming the providers you accept turns the
     * middle tier into "someone an identity provider I named vouches for".
     *
     * <p>Unset means allow all, so this changes nothing until it is configured.
     */
    public com.ebremer.lws.auth.oidc.TrustPolicy issuerPolicy() {
        return issuerPolicy;
    }

    /**
     * Which hosts may serve a WebID, from {@code :AllowedWebIdHost} and {@code :DeniedWebIdHost}.
     *
     * <p>Separate from {@link #issuerPolicy()} and usually not needed: the provider is the trust
     * anchor, since it is what signs the token, and a provider you trust will not vouch for a WebID
     * it does not control. Constrain this as well when the deployment also wants the identifiers
     * themselves to live somewhere it recognises. Unset means allow all.
     */
    public com.ebremer.lws.auth.oidc.TrustPolicy webIdHostPolicy() {
        return webIdHostPolicy;
    }

    /** The configured storages, in declaration order. Possibly empty. */
    public List<LwsStorageConfig> storages() {
        return storages;
    }

    /** Directory of the module's own TDB2 instance, separate from Halcyon's. */
    public String storeLocation() {
        return storeLocation;
    }

    /**
     * WebID of the storage controller, from {@code :LWSOwner}.
     *
     * <p>Null when unset, in which case the bootstrap policy grants full control to
     * any <em>authenticated</em> agent instead. That is a deliberate development
     * default — a storage whose root ACR named nobody would be unwritable by anyone,
     * including the person trying to configure it — but it is a permissive one, and
     * {@code AcpBootstrap} logs a warning saying so. Anonymous access is never
     * granted by default either way.
     */
    public String owner() {
        return owner;
    }

    /**
     * Whether a notification includes the {@code actor} — the agent that made the change.
     *
     * <p>From {@code :LWSIncludeActor}, default {@code false}. lws10-notifications says the
     * {@code actor} "SHOULD be omitted by default" (it discloses who touched a resource) but a
     * server "MAY make its inclusion configurable"; this is that switch. See
     * {@link com.ebremer.lws.notify.Notifications}.
     */
    public boolean includeActor() {
        return includeActor;
    }

    /**
     * Whether a bulk operation delivers its activities as one batched envelope.
     *
     * <p>From {@code :LWSBatchNotifications}, default {@code false}. lws10-notifications lets a
     * server "combine multiple activities into a single notification envelope by providing an array
     * of activity objects" (a MAY). When off, a recursive delete notifies only about the container
     * itself, as before; when on, it notifies about the whole removed subtree in one envelope per
     * subscriber (each filtered to what that subscriber may see).
     */
    public boolean batchNotifications() {
        return batchNotifications;
    }

    /**
     * Whether {@code Prefer: set-linkset} is honoured — a combined content-and-metadata update.
     *
     * <p>From {@code :LWSSetLinkset}, default {@code false}. lws10-core makes the combined update
     * OPTIONAL: a client PUT/PATCHes the resource with {@code Link} headers and {@code Prefer:
     * set-linkset}, and the server interprets those links as a replacement (PUT) or partial update
     * (PATCH) of the linkset, atomically with the content change. A server that does not support it
     * "MUST ignore the preference or respond with 501" — this flag off is the ignore path.
     */
    public boolean setLinkset() {
        return setLinkset;
    }

    /**
     * The storage that holds PER-USER application data (e.g. the annotation
     * color classes at {@code {storage}/users/{name}/…}): the one named by
     * {@code :LWSUserDataStorage "<urlPath>"}, else the first slug-named
     * (mirror) storage, else {@code null} when none qualifies.
     */
    public LwsStorageConfig userDataStorage() {
        if (userDataStoragePath != null) {
            for (LwsStorageConfig cfg : storages) {
                if (cfg.urlPath().equals(userDataStoragePath)) {
                    return cfg;
                }
            }
            LOG.warn(":LWSUserDataStorage {} names no configured storage — falling back", userDataStoragePath);
        }
        for (LwsStorageConfig cfg : storages) {
            if (cfg.naming() == NamingPolicyType.SLUG) {
                return cfg;
            }
        }
        return null;
    }

    private static String readString(Model m, String localName) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                "select ?v where { ?s :" + localName + " ?v }");
        pss.setNsPrefix("", HAL.NS);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                var n = rs.next().get("v");
                if (n != null && n.isLiteral()) {
                    return n.asLiteral().getString().trim();
                }
            }
        }
        return null;
    }

    private static boolean readBoolean(Model m, String localName, boolean dflt) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                "select ?v where { ?s :" + localName + " ?v }");
        pss.setNsPrefix("", HAL.NS);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                var n = rs.next().get("v");
                if (n != null && n.isLiteral()) {
                    try {
                        return n.asLiteral().getBoolean();
                    } catch (RuntimeException e) {
                        return Boolean.parseBoolean(n.asLiteral().getString().trim());
                    }
                }
            }
        }
        return dflt;
    }

    /** Every value of a repeatable settings property, in declaration order. */
    private static java.util.List<String> readAll(Model m, String localName) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                "select ?o where { ?s :" + localName + " ?o }");
        pss.setNsPrefix("", HAL.NS);
        java.util.List<String> out = new java.util.ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                var n = rs.next().get("o");
                if (n == null) {
                    continue;
                }
                out.add(n.isURIResource() ? n.asResource().getURI() : n.asLiteral().getString());
            }
        }
        return out;
    }

    private static String readOwner(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                "select ?o where { ?s :LWSOwner ?o }");
        pss.setNsPrefix("", HAL.NS);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                var n = rs.next().get("o");
                return n.isURIResource() ? n.asResource().getURI() : n.asLiteral().getString();
            }
        }
        return null;
    }

    private static Model load() {
        File f = new File(SETTINGS_FILE);
        if (!f.exists()) {
            LOG.warn("{} not found in {} — no LWS storages will be mounted",
                    SETTINGS_FILE, new File(".").getAbsolutePath());
            return ModelFactory.createDefaultModel();
        }
        return RDFDataMgr.loadModel(f.toURI().toString());
    }

    private static String readStoreLocation(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                "select ?loc where { ?s :LWSStoreLocation ?loc }");
        pss.setNsPrefix("", HAL.NS);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                return rs.next().getLiteral("loc").getString();
            }
        }
        return DEFAULT_TDB2;
    }

    private static List<LwsStorageConfig> readStorages(Model m) {
        String siteUrl = HalcyonSettings.getSettings().getProxyHostName();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?st ?urlPath ?storageRoot ?namingPolicy ?backend
            where {
                ?s :hasLWSStorage ?st .
                ?st :urlPath      ?urlPath ;
                    :storageRoot  ?storageRoot ;
                    :namingPolicy ?namingPolicy
                optional { ?st :hasBackend ?backend }
            } order by ?urlPath
            """);
        pss.setNsPrefix("", HAL.NS);

        List<LwsStorageConfig> out = new ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String urlPath = sol.getLiteral("urlPath").getString();
                String root = sol.getResource("storageRoot").getURI();
                String naming = sol.getLiteral("namingPolicy").getString();
                try {
                    LwsStorageConfig cfg = new LwsStorageConfig(
                            urlPath,
                            Path.of(URI.create(root)),
                            NamingPolicyType.parse(naming),
                            siteUrl,
                            readMounts(m, sol.get("st"), urlPath),
                            sol.contains("backend") ? sol.getResource("backend") : null);
                    out.add(cfg);
                    LOG.info("LWS storage {} -> {} ({} naming{}{})",
                            cfg.urlPath(), cfg.contentRoot(), cfg.naming(),
                            cfg.mounts().isEmpty() ? "" : ", " + cfg.mounts().size() + " mount(s)",
                            cfg.backend() == null ? "" : ", pluggable backend");
                } catch (RuntimeException ex) {
                    // One malformed declaration must not take the whole app down.
                    LOG.error("ignoring malformed LWS storage declaration urlPath={} storageRoot={} namingPolicy={}: {}",
                            urlPath, root, naming, ex.getMessage());
                }
            }
        }
        if (out.isEmpty()) {
            LOG.info("no :hasLWSStorage declarations in {} — LWS storages disabled", SETTINGS_FILE);
        }
        return List.copyOf(out);
    }

    /**
     * The storage's {@code :hasMount} declarations — other physical disks backing
     * sub-containers (mirror storages only; see {@link LwsMount}):
     * <pre>{@code
     * :hasMount [ :containerPath "tcga/brca" ; :resourceBase <file:///F:/brca/> ]
     * }</pre>
     * Lenient per mount: a malformed one is logged and skipped, the storage and its
     * other mounts stand. Duplicate container paths keep the first declaration.
     */
    private static List<LwsMount> readMounts(Model m, org.apache.jena.rdf.model.RDFNode storage,
            String urlPath) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?path ?base
            where { ?st :hasMount [ :containerPath ?path ; :resourceBase ?base ] }
            order by ?path
            """);
        pss.setNsPrefix("", HAL.NS);
        // The storage node is usually a BLANK node, which cannot ride setIri —
        // bind it through query substitution instead.
        List<LwsMount> out = new ArrayList<>();
        var query = org.apache.jena.query.QueryFactory.create(pss.toString());
        var initial = new org.apache.jena.query.QuerySolutionMap();
        initial.add("st", storage);
        try (QueryExecution qe = QueryExecution.model(m).query(query).substitution(initial).build()) {
            ResultSet rs = qe.execSelect();
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String path = sol.getLiteral("path").getString();
                String base = sol.getResource("base").getURI();
                try {
                    LwsMount mount = new LwsMount(path, Path.of(URI.create(base)));
                    if (!seen.add(mount.containerPath())) {
                        LOG.error("ignoring duplicate LWS mount {} on {}", mount.containerPath(), urlPath);
                        continue;
                    }
                    out.add(mount);
                    LOG.info("LWS storage {} mount: {} -> {}", urlPath,
                            mount.containerPath(), mount.root());
                } catch (RuntimeException ex) {
                    LOG.error("ignoring malformed LWS mount containerPath={} resourceBase={} on {}: {}",
                            path, base, urlPath, ex.getMessage());
                }
            }
        }
        return out;
    }
}

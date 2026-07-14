package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Access Control Policy (ACP) vocabulary — the authorization model for this
 * storage.
 *
 * <p>Evaluation (implemented in {@code com.ebremer.lws.acp.AcpEngine}):
 * <ul>
 *   <li>The <em>effective policies</em> for a resource are those applied by its
 *       own ACR via {@link #accessControl}, plus those applied by an ancestor's
 *       ACR via {@link #memberAccessControl}, inherited transitively.</li>
 *   <li>A policy is <em>satisfied</em> iff every {@link #allOf} matcher matches,
 *       at least one {@link #anyOf} matcher matches (vacuously true when none are
 *       given), and no {@link #noneOf} matcher matches.</li>
 *   <li>A matcher matches iff, for every attribute it defines ({@link #agent},
 *       {@link #client}, {@link #issuer}, {@link #vc}), at least one of its values
 *       matches the request context.</li>
 *   <li>A mode is granted iff some satisfied policy {@link #allow}s it and no
 *       satisfied policy {@link #deny}s it. <strong>Deny wins.</strong></li>
 * </ul>
 *
 * @see <a href="https://solid.github.io/authorization-panel/acp-specification/">ACP</a>
 */
public final class ACP {

    public static final String NS = "http://www.w3.org/ns/solid/acp#";

    public static String getURI() {
        return NS;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    private static Property prop(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    // --- Classes -----------------------------------------------------------

    public static final Resource AccessControlResource = cls("AccessControlResource");
    public static final Resource AccessControl = cls("AccessControl");
    public static final Resource Policy = cls("Policy");
    public static final Resource Matcher = cls("Matcher");
    public static final Resource AccessGrant = cls("AccessGrant");
    public static final Resource Context = cls("Context");

    // --- Attaching an ACR to the resources it controls ----------------------

    /** Links an ACR to the resource it controls. */
    public static final Property resource = prop("resource");

    /** Access controls that apply to the resource itself. */
    public static final Property accessControl = prop("accessControl");

    /**
     * Access controls that apply to the resource's members, transitively. This is
     * the inheritance mechanism: a policy attached here at the storage root
     * reaches every descendant.
     */
    public static final Property memberAccessControl = prop("memberAccessControl");

    /** Links an access control to the policies it applies. */
    public static final Property apply = prop("apply");

    // --- Policy effects -----------------------------------------------------

    /** Modes granted when the policy is satisfied. */
    public static final Property allow = prop("allow");

    /** Modes revoked when the policy is satisfied. Overrides {@link #allow}. */
    public static final Property deny = prop("deny");

    // --- Matcher combinators ------------------------------------------------

    /** Every referenced matcher must match (AND). */
    public static final Property allOf = prop("allOf");

    /** At least one referenced matcher must match (OR). */
    public static final Property anyOf = prop("anyOf");

    /** No referenced matcher may match (NOR). */
    public static final Property noneOf = prop("noneOf");

    // --- Context attributes a matcher can constrain -------------------------

    public static final Property agent = prop("agent");
    public static final Property client = prop("client");
    public static final Property issuer = prop("issuer");
    public static final Property vc = prop("vc");
    public static final Property target = prop("target");
    public static final Property creator = prop("creator");
    public static final Property owner = prop("owner");
    public static final Property grant = prop("grant");
    public static final Property context = prop("context");

    // --- Well-known agents --------------------------------------------------

    /** Matches every request, authenticated or not. */
    public static final Resource PublicAgent = cls("PublicAgent");

    /** Matches any request carrying a valid authentication credential. */
    public static final Resource AuthenticatedAgent = cls("AuthenticatedAgent");

    /** Matches when the requesting agent created the target resource. */
    public static final Resource CreatorAgent = cls("CreatorAgent");

    /** Matches when the requesting agent owns the target resource. */
    public static final Resource OwnerAgent = cls("OwnerAgent");

    private ACP() {
    }
}

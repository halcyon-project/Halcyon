package com.ebremer.lws.s3;

import com.ebremer.ns.HAL;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The S3 backend's settings vocabulary, in Halcyon's own namespace so a
 * {@code settings.ttl} keeps its single {@code :} prefix:
 * <pre>{@code
 * :hasLWSStorage [ a lws:Storage ; :urlPath "/bremerstore" ;
 *                  :storageRoot <file:///D:/lws-cache/bremerstore/> ; :namingPolicy "uuid" ;
 *                  :hasBackend  [ a :S3 ;
 *                                 :s3Bucket "bremerstore" ;
 *                                 :s3Region "us-east-1" ;
 *                                 :s3Prefix "pods/main" ;          # optional
 *                                 :s3Endpoint <http://minio:9000> ; # optional (S3-compatible)
 *                                 :s3ForcePathStyle true ] ] .      # optional
 * }</pre>
 * Credentials deliberately have no vocabulary: the AWS default provider chain
 * (environment, profile, instance role) is the right place for secrets, not a
 * settings file that gets passed around.
 */
public final class S3Vocab {

    private S3Vocab() {
    }

    public static final String NS = HAL.NS;

    /** The backend node's type: {@code :hasBackend [ a :S3 ; ... ]}. */
    public static final Resource S3 = ResourceFactory.createResource(NS + "S3");

    public static final Property bucket = ResourceFactory.createProperty(NS + "s3Bucket");
    public static final Property region = ResourceFactory.createProperty(NS + "s3Region");
    public static final Property prefix = ResourceFactory.createProperty(NS + "s3Prefix");
    public static final Property endpoint = ResourceFactory.createProperty(NS + "s3Endpoint");
    public static final Property forcePathStyle = ResourceFactory.createProperty(NS + "s3ForcePathStyle");
}

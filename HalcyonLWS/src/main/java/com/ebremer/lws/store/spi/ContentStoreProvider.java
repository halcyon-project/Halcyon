package com.ebremer.lws.store.spi;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.ContentStore;

/**
 * Pluggable {@link ContentStore} backends, discovered via {@link java.util.ServiceLoader}
 * ({@code META-INF/services/com.ebremer.lws.store.spi.ContentStoreProvider}).
 *
 * <p>A storage declared in {@code settings.ttl} may carry a backend description:
 * <pre>{@code
 * :hasLWSStorage [ a lws:Storage ; :urlPath "/bremerstore" ;
 *                  :storageRoot <file:///D:/lws-cache/bremerstore/> ; :namingPolicy "uuid" ;
 *                  :hasBackend  [ a :S3 ; :s3Bucket "bremerstore" ; :s3Region "us-east-1" ] ] .
 * }</pre>
 * The backend node rides {@link LwsStorageConfig#backend()} verbatim; a provider recognises
 * its own node (typically by {@code rdf:type}) in {@link #supports} and parses its own
 * vocabulary in {@link #create}. Core stays vocabulary-free.
 *
 * <p>Division of labour is deliberate: <em>developers</em> ship providers on the classpath,
 * <em>operators</em> declare storages against them in {@code settings.ttl}, users never see
 * either — over the protocol every storage is just an LWS storage.
 *
 * <p>{@link #create} runs once per storage at boot. Refuse bad configuration loudly there
 * ({@link IllegalArgumentException} with a message an operator can act on): the host logs the
 * failure and skips mounting that one storage rather than letting a lame one boot. A remote
 * backend must come back wrapped so {@link ContentStore#pathFor} yields a real local file —
 * see {@code MaterializedContentStore}.
 */
public interface ContentStoreProvider {

    /** Whether this provider recognises the storage's backend declaration. */
    boolean supports(LwsStorageConfig cfg);

    /** Build the storage's content store. Called once per storage, at boot. */
    ContentStore create(LwsStorageConfig cfg);
}

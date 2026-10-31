package com.tasklean.api.common.storage;

import java.util.Optional;

/**
 * The bucket, as the rest of the application sees it. Everything above this port deals in keys and
 * signed URLs, never in an S3 SDK type — so a different provider is a new adapter, and a service
 * test needs no network.
 *
 * <p>Image bytes never pass through this process: the browser PUTs to and GETs from the bucket
 * itself, and this port only signs those requests and inspects the result.
 */
public interface ObjectStorage {

    /**
     * Signs a URL the browser may PUT one object to. The content type and length are part of the
     * signature, so the upload is refused unless it matches what was asked for.
     *
     * @param key           the object key to write
     * @param contentType   the exact {@code Content-Type} the upload must send
     * @param contentLength the exact byte count the upload must send
     * @return the signed upload URL
     */
    String presignUpload(String key, String contentType, long contentLength);

    /**
     * Signs a short-lived URL the object can be read from.
     *
     * @param key the object key to read
     * @return the signed download URL
     */
    String presignDownload(String key);

    /**
     * Reads an object's metadata, to confirm an upload actually arrived as declared.
     *
     * @param key the object key
     * @return the object's type and size, or empty if there is no such object
     */
    Optional<StoredObject> head(String key);

    /**
     * Reads the first bytes of an object, enough to check its magic number — a declared content
     * type is only a claim until the bytes agree with it.
     *
     * @param key   the object key
     * @param count how many leading bytes to read
     * @return the leading bytes, or empty if there is no such object
     */
    Optional<byte[]> readPrefix(String key, int count);

    /**
     * Deletes an object. Succeeds silently when the object is already gone, so a retried sweep is
     * not an error.
     *
     * @param key the object key
     */
    void delete(String key);
}

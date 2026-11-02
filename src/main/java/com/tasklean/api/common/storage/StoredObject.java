package com.tasklean.api.common.storage;

/**
 * What the bucket reports about one stored object.
 *
 * @param contentType   the type the object was stored with
 * @param contentLength the object's size in bytes
 */
public record StoredObject(String contentType, long contentLength) {
}

package com.tasklean.api.domain.upload;

/**
 * Where an upload is in its life. Nothing is deleted from the table: a row ends in a terminal
 * state, which is what lets the sweep tell "nobody ever used this" from "this was replaced".
 */
public enum UploadStatus {

    /** Signed for, not yet pointed at by anything. The sweep collects these after a day. */
    PENDING,

    /** Attached to the entity it was requested for. */
    ATTACHED,

    /** Was attached, then replaced or cleared. Its object is collected by the sweep. */
    SUPERSEDED,

    /** Its object has been deleted from the bucket. */
    DELETED
}

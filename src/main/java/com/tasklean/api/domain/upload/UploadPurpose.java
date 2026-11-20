package com.tasklean.api.domain.upload;

/**
 * What an uploaded image is for. The purpose decides who may ask for the upload and which key
 * prefix the object lands under, so a new kind of image is a new constant here plus its
 * authorization rule in {@code UploadSecurity} — never a flag on an existing one.
 */
public enum UploadPurpose {

    /** A household's picture. Only that group's admin may ask for one. */
    GROUP_PHOTO("group-photo"),

    /** A person's own profile picture. Only that person may ask for one. */
    USER_PHOTO("user-photo"),

    /** A chore's reference picture - what "done" is meant to look like. Targets the household,
     * because no task exists yet while its picture is being chosen. */
    TASK_PHOTO("task-photo"),

    /** Proof that a round of a chore was done. Targets the task, which does exist by then. */
    COMPLETION_PHOTO("completion-photo");

    private final String keyPrefix;

    UploadPurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Returns the key prefix objects of this purpose are stored under.
     *
     * @return the prefix, with no trailing slash
     */
    public String keyPrefix() {
        return keyPrefix;
    }
}

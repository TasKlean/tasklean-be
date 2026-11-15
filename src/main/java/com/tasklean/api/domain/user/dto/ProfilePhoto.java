package com.tasklean.api.domain.user.dto;

import com.tasklean.api.domain.user.User;

/**
 * Which picture a response shows for a person. An account can have two sources - an upload of ours
 * and the picture Google provisioned it with - and a response publishes exactly one of them.
 */
record ProfilePhoto(String uid, String url) {

    /**
     * Picks the picture to publish. Ours wins when the account has one; the bucket is private, so
     * it travels as an opaque uid the client resolves through {@code GET /api/uploads/{uid}}, never
     * as a signed URL in the body.
     *
     * @param user the account being mapped
     * @return the uid of our upload, or the URL Google gave us - never both
     */
    static ProfilePhoto of(User user) {
        return user.getPhotoUid() != null
                ? new ProfilePhoto(user.getPhotoUid(), null)
                : new ProfilePhoto(null, user.getPhotoUrl());
    }
}

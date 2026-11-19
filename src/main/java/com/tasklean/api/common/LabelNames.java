package com.tasklean.api.common;

/**
 * Cleans the name of a group-scoped label — a category or a tag — before it is stored or matched.
 * One place for it because the uniqueness rule is shared: a name typed into a tag field and one
 * sent to the category endpoint have to normalise identically, or two spellings of the same word
 * end up as two rows.
 */
public final class LabelNames {

    private LabelNames() {
    }

    /**
     * Trims the name and collapses any run of whitespace inside it to a single space. Case is
     * left alone — the name is shown back to the household as they typed it, and uniqueness is
     * enforced case-insensitively by the index rather than by flattening what they wrote.
     *
     * @param name the name as the caller sent it, possibly null
     * @return the cleaned name, or null if the input was null
     */
    public static String normalise(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }
}

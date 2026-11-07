package com.tasklean.api.domain.auditlog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Collects which fields an update actually changed, so an audit entry can name them. Built as the
 * update applies its values - the old value has to be read before the setter overwrites it, which
 * is why {@code set} does both.
 */
public final class AuditChanges {

    private final List<String> fields = new ArrayList<>();

    private AuditChanges() {
    }

    /**
     * Starts an empty set of changes.
     *
     * @return a new collector
     */
    public static AuditChanges create() {
        return new AuditChanges();
    }

    /**
     * Records the field as changed if the new value differs from the current one, then applies it.
     * Setting happens either way, so a value that is not a meaningful change (blank over absent) is
     * still stored exactly as before this existed.
     *
     * @param field    the field's name as the request DTO spells it
     * @param getter   reads the current value
     * @param setter   applies the new value
     * @param newValue the value to apply
     * @param <T>      the field's type
     * @return this collector, for chaining
     */
    public <T> AuditChanges set(String field, Supplier<T> getter, Consumer<T> setter, T newValue) {
        if (differs(getter.get(), newValue)) {
            fields.add(field);
        }
        setter.accept(newValue);
        return this;
    }

    /**
     * Records the field as changed if the two values differ, without applying anything. For a field
     * whose compared value is not what gets stored - an association is set as an entity but compared
     * by id, since two loads of one row are not the same object.
     *
     * @param field    the field's name as the request DTO spells it
     * @param oldValue the value before the update
     * @param newValue the value after it
     * @return this collector, for chaining
     */
    public AuditChanges track(String field, Object oldValue, Object newValue) {
        if (differs(oldValue, newValue)) {
            fields.add(field);
        }
        return this;
    }

    /**
     * Whether the update left everything as it was.
     *
     * @return true if no field changed
     */
    boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * The changed field names, in the order the update applied them.
     *
     * @return an immutable copy
     */
    public List<String> fields() {
        return List.copyOf(fields);
    }

    private static boolean differs(Object oldValue, Object newValue) {
        return !Objects.equals(normalize(oldValue), normalize(newValue));
    }

    // Blank text and no text are the same absence, so "" arriving over null is not a change.
    private static Object normalize(Object value) {
        return value instanceof String text && text.isBlank() ? null : value;
    }
}

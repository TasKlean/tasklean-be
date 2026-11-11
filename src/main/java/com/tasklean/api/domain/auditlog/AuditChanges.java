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
 *
 * <p>A field's value is withheld unless the call site asks for it with {@code setVisible}. The
 * default is the safe one: the trail is append-only and a group's is read by every member, so a
 * value kept here is kept forever and shown to everybody who can see the entry.
 */
public final class AuditChanges {

    private final List<AuditFieldChange> entries = new ArrayList<>();

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
     * The value itself is withheld. Setting happens either way, so a value that is not a meaningful
     * change (blank over absent) is still stored exactly as before this existed.
     *
     * @param field    the field's name as the request DTO spells it
     * @param getter   reads the current value
     * @param setter   applies the new value
     * @param newValue the value to apply
     * @param <T>      the field's type
     * @return this collector, for chaining
     */
    public <T> AuditChanges set(String field, Supplier<T> getter, Consumer<T> setter, T newValue) {
        return applyAndRecord(field, getter, setter, newValue, false);
    }

    /**
     * As {@link #set}, but keeps what the field moved between. Only for a value that is safe in an
     * append-only trail every reader of the entry can see: an enum, a flag, a number, a date, or a
     * presentational token. Never text a person wrote, never a secret, never an internal id.
     *
     * @param field    the field's name as the request DTO spells it
     * @param getter   reads the current value
     * @param setter   applies the new value
     * @param newValue the value to apply
     * @param <T>      the field's type
     * @return this collector, for chaining
     */
    public <T> AuditChanges setVisible(String field, Supplier<T> getter, Consumer<T> setter, T newValue) {
        return applyAndRecord(field, getter, setter, newValue, true);
    }

    /**
     * Records the field as changed if the two values differ, without applying anything and without
     * keeping either. For a field whose compared value is not what gets stored - an association is
     * set as an entity but compared by id, since two loads of one row are not the same object.
     *
     * @param field    the field's name as the request DTO spells it
     * @param oldValue the value before the update
     * @param newValue the value after it
     * @return this collector, for chaining
     */
    public AuditChanges track(String field, Object oldValue, Object newValue) {
        if (differs(oldValue, newValue)) {
            entries.add(AuditFieldChange.withheld(field));
        }
        return this;
    }

    /**
     * Whether the update left everything as it was.
     *
     * @return true if no field changed
     */
    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * The changed field names, in the order the update applied them.
     *
     * @return an immutable copy
     */
    public List<String> fields() {
        return entries.stream().map(AuditFieldChange::field).toList();
    }

    /**
     * The changes themselves, in the order the update applied them, each with its values or without
     * them as the call site decided.
     *
     * @return an immutable copy
     */
    public List<AuditFieldChange> entries() {
        return List.copyOf(entries);
    }

    private <T> AuditChanges applyAndRecord(String field, Supplier<T> getter, Consumer<T> setter,
                                            T newValue, boolean keepValues) {
        T oldValue = getter.get();
        if (differs(oldValue, newValue)) {
            entries.add(keepValues
                    ? AuditFieldChange.shown(field, oldValue, newValue) : AuditFieldChange.withheld(field));
        }
        setter.accept(newValue);
        return this;
    }

    private static boolean differs(Object oldValue, Object newValue) {
        return !Objects.equals(normalize(oldValue), normalize(newValue));
    }

    // Blank text and no text are the same absence, so "" arriving over null is not a change.
    private static Object normalize(Object value) {
        return value instanceof String text && text.isBlank() ? null : value;
    }
}

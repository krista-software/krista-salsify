package com.krista.krista.extension.salsify.utils;

/**
 * Responsible solely for normalising raw Salsify field values to plain English.
 *
 * <h3>Value rules</h3>
 * <ul>
 *   <li>{@code "Y"} / {@code "true"}  → {@code "Yes"}</li>
 *   <li>{@code "N"} / {@code "false"} → {@code "No"}</li>
 *   <li>All other values pass through unchanged.</li>
 * </ul>
 */
public final class FieldMapper {

    private FieldMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Normalises a raw field value.
     *
     * @param value the raw value produced by {@code elementToValue()} — may be {@code null}
     * @return normalised value, or {@code null} if input is {@code null}
     */
    public static Object mapValue(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        if (Constants.STATUS_DISCONTINUED.equals(s)) return Constants.BOOLEAN_YES;
        if (Constants.STATUS_ACTIVE.equals(s)) return Constants.BOOLEAN_NO;
        if (Constants.BOOLEAN_TRUE_LOWERCASE.equals(s)) return Constants.BOOLEAN_YES;
        if (Constants.BOOLEAN_FALSE_LOWERCASE.equals(s)) return Constants.BOOLEAN_NO;
        return value;
    }
}

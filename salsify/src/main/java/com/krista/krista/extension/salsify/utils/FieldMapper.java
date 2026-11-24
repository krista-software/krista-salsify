/*
 * Salsify Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

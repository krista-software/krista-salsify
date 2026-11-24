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

package com.krista.krista.extension.salsify.config;

/**
 * Attribute key constants for the Salsify extension.
 * These names map directly to the @Field.Text annotations on SalsifyExtension
 * and are used when reading from the Invoker attributes map.
 */
public final class AttributeKeys {

    private AttributeKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Display name and attribute key for the Salsify Organisation ID. */
    public static final String ORG_ID = "Salsify Org ID";

    /** Display name and attribute key for the Salsify API Key (secured). */
    public static final String API_KEY = "Salsify API Key";
}

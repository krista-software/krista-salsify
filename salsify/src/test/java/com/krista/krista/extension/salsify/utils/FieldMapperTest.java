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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class FieldMapperTest {

    @Test
    void mapValue_null_returnsNull() {
        assertNull(FieldMapper.mapValue(null));
    }

    @Test
    void mapValue_Y_returnsYes() {
        assertEquals("Yes", FieldMapper.mapValue("Y"));
    }

    @Test
    void mapValue_N_returnsNo() {
        assertEquals("No", FieldMapper.mapValue("N"));
    }

    @Test
    void mapValue_true_returnsYes() {
        assertEquals("Yes", FieldMapper.mapValue("true"));
    }

    @Test
    void mapValue_false_returnsNo() {
        assertEquals("No", FieldMapper.mapValue("false"));
    }

    @Test
    void mapValue_otherString_returnsOriginal() {
        assertEquals("Active", FieldMapper.mapValue("Active"));
    }

    @Test
    void mapValue_integer_returnsOriginal() {
        assertEquals(42, FieldMapper.mapValue(42));
    }

    @Test
    void mapValue_whitespaceAroundY_returnsYes() {
        assertEquals("Yes", FieldMapper.mapValue("  Y  "));
    }

    @Test
    void mapValue_whitespaceAroundN_returnsNo() {
        assertEquals("No", FieldMapper.mapValue("  N  "));
    }

    @Test
    void constructor_isPrivate() throws Exception {
        Constructor<FieldMapper> c = FieldMapper.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertThrows(InvocationTargetException.class, c::newInstance);
    }
}

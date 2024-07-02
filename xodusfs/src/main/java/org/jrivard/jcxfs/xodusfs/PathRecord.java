/*
 * Copyright 2024 Jason D. Rivard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jrivard.jcxfs.xodusfs;

import java.util.HexFormat;
import java.util.Objects;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;

record PathRecord(long id, String name) {
    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final String SEPARATOR = "!";
    private static final String VERSION = "1";

    public PathRecord {
        if (id < 0) {
            throw new IllegalArgumentException("id can not be negative");
        }

        if (Objects.requireNonNull(name).isEmpty()) {
            throw new IllegalArgumentException("name must have at least one character");
        }
    }

    public static PathRecord fromByteIterable(final ByteIterable byteIterable) {
        final String stringInput = StringBinding.entryToString(byteIterable);
        Objects.requireNonNull(stringInput);

        final String[] splitString = stringInput.split(SEPARATOR);
        if (splitString.length < 3) {
            throw new IllegalArgumentException("deserialized record missing components");
        }
        if (!VERSION.equals(splitString[0])) {
            throw new IllegalArgumentException("deserialized record version not recognized");
        }
        final long id = Long.decode('#' + splitString[1]);
        final String name = splitString[2];
        return new PathRecord(id, name);
    }

    public ByteIterable toByteIterable() {
        final String idAsHex = HEX_FORMAT.toHexDigits(id);
        final String stringOutput = VERSION + SEPARATOR + idAsHex + SEPARATOR + name;
        return StringBinding.stringToEntry(stringOutput);
    }
}

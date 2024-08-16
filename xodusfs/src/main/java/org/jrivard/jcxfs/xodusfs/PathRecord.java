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

        final int FIRST_INDEX = stringInput.indexOf(SEPARATOR);
        final int SECOND_INDEX = stringInput.indexOf(SEPARATOR, FIRST_INDEX + 1);
        if (FIRST_INDEX <= 0 || SECOND_INDEX <= 0) {
            throw new IllegalArgumentException("deserialized record missing components");
        }

        {
            final String versionSegment = stringInput.substring(0, FIRST_INDEX);
            if (!VERSION.equals(versionSegment)) {
                throw new IllegalArgumentException("deserialized record version not recognized");
            }
        }

        final String idSegment = stringInput.substring(FIRST_INDEX + 1, SECOND_INDEX);
        final long id = Long.decode('#' + idSegment);
        final String name = stringInput.substring(SECOND_INDEX + 1);
        return new PathRecord(id, name);
    }

    public ByteIterable toByteIterable() {
        final String idAsHex = HEX_FORMAT.toHexDigits(id);
        final String stringOutput = VERSION + SEPARATOR + idAsHex + SEPARATOR + name;
        return StringBinding.stringToEntry(stringOutput);
    }
}

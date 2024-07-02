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
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.LongBinding;

final class InodeId {
    public static final int ROOT_INODE = 1;

    static ByteIterable rootId() {
        return inodeIdToByteIterable(ROOT_INODE);
    }

    static ByteIterable inodeIdToByteIterable(final long value) {
        return LongBinding.signedLongToCompressedEntry(value);
    }

    static long byteIterableToInodeId(final ByteIterable byteIterable) {
        return LongBinding.compressedEntryToSignedLong(byteIterable);
    }

    static String prettyPrint(final long value) {
        return HexFormat.of().toHexDigits(value);
    }
}

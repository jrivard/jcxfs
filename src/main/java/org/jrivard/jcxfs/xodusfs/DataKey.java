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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;

public record DataKey(long fid, int page) implements StoreKey {

    public DataKey {
        validate(fid, page);
    }

    public static DataKey fromByteIterable(final ByteIterable byteIterable) {
        final ByteArrayInputStream baos = new ByteArrayInputStream(byteIterable.getBytesUnsafe());
        final DataInputStream dataInputStream = new DataInputStream(baos);
        try {
            final long fid = dataInputStream.readLong();
            final int page = dataInputStream.readInt();
            return new DataKey(fid, page);
        } catch (final IOException e) {
            throw new RuntimeException("internal error generating byteiterable key: " + e.getMessage());
        }
    }

    @Override
    public ByteIterable toByteIterable() {
        return toByteIterableImplementation(fid, page);
    }

    public static ByteIterable toByteIterable(final long fid, final int page) {
        validate(fid, page);
        return toByteIterableImplementation(fid, page);
    }

    private static void validate(final long fid, final int page) {
        if (fid <= 0) {
            throw new IllegalArgumentException("fid value must be a positive long");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page value must be a positive long");
        }
    }

    private static ByteIterable toByteIterableImplementation(final long fid, final int page) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        try {
            dos.writeLong(fid);
            dos.writeInt(page);
        } catch (final IOException e) {
            throw new RuntimeException("internal error generating byteiterable key: " + e.getMessage());
        }
        final byte[] bytes = baos.toByteArray();
        return new ArrayByteIterable(bytes, bytes.length);
    }
}

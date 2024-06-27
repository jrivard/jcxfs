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

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import org.cryptomator.jfuse.api.Stat;
import org.jrivard.jcxfs.JsonUtil;

public record InodeEntry(
        @SerializedName("m") int mode,
        @SerializedName("at") Instant aTime,
        @SerializedName("ct") Instant cTime,
        @SerializedName("bt") Instant bTime,
        @SerializedName("mt") Instant mTime,
        @SerializedName("u") int uid,
        @SerializedName("g") int gid,
        @SerializedName("p") String targetPath) {

    private static final Set<Type> ALL_TYPES = EnumSet.allOf(Type.class);

    private enum Type {
        DIR(Stat.S_ISDIR, Stat.S_IFDIR, Stat.S_IFDIR | 0755),
        FILE(Stat.S_ISREG, Stat.S_IFREG, Stat.S_IFREG | 0444),
        LINK(Stat.S_ISLNK, Stat.S_IFLNK, Stat.S_IFLNK | 0444),
        ;

        private final IntPredicate predicate;
        private final int mask;
        private final int initialValue;

        Type(final IntPredicate predicate, final int mask, final int initialValue) {
            this.predicate = predicate;
            this.mask = mask;
            this.initialValue = initialValue;
        }

        public boolean test(final int mode) {
            return predicate.test(mode);
        }

        public int mask() {
            return mask;
        }

        public int initialValue() {
            return initialValue;
        }
    }

    public InodeEntry {
        if (ALL_TYPES.stream().filter(t -> t.test(mode)).findAny().isEmpty()) {
            throw new IllegalArgumentException("unknown file type in mask");
        }
        Objects.requireNonNull(cTime);
        Objects.requireNonNull(mTime);
        Objects.requireNonNull(bTime);
    }

    public static InodeEntry newFileEntry() {
        return newEntry(Type.FILE, Type.FILE.initialValue());
    }

    public static InodeEntry newFileEntry(final int mode) {
        return newEntry(Type.FILE, mode);
    }

    public static InodeEntry newLinkEntry() {
        return newEntry(Type.LINK, Type.LINK.initialValue());
    }

    public static InodeEntry newLinkEntry(final int mode) {
        return newEntry(Type.LINK, mode);
    }

    public static InodeEntry newDirectoryEntry() {
        return newEntry(Type.DIR, Type.DIR.initialValue());
    }

    public static InodeEntry newDirectoryEntry(final int mode) {
        return newEntry(Type.DIR, mode);
    }

    private static InodeEntry newEntry(final Type type, final int mode) {
        final int effectiveMode = type.mask() | mode;
        return new InodeEntry(effectiveMode, Instant.now(), Instant.now(), Instant.now(), Instant.now(), 0, 0, null);
    }

    public InodeEntry withMtimeNow() {
        return new InodeEntry(mode, aTime, cTime, bTime, Instant.now(), uid, gid, targetPath);
    }

    public InodeEntry withUidGid(final int uid, final int gid) {
        return new InodeEntry(mode, aTime, cTime, bTime, mTime, uid, gid, targetPath);
    }

    public InodeEntry withAtimeMtime(final Instant aTime, final Instant mTime) {
        return new InodeEntry(mode, aTime, cTime, bTime, mTime, uid, gid, targetPath);
    }

    public InodeEntry withMode(final int mode) {
        return new InodeEntry(mode, aTime, cTime, bTime, mTime, uid, gid, targetPath);
    }

    public InodeEntry withTargetPath(final String targetPath) {
        return new InodeEntry(mode, aTime, cTime, bTime, mTime, uid, gid, targetPath);
    }

    public static InodeEntry fromByteIterable(final ByteIterable byteIterable) {
        Objects.requireNonNull(byteIterable);
        final String json = StringBinding.entryToString(byteIterable);
        Objects.requireNonNull(json);
        try {
            return JsonUtil.deserialize(json, InodeEntry.class);
        } catch (final Exception e) {
            throw new RuntimeException("error decoding stored directory entry: " + e.getMessage());
        }
    }

    public ByteIterable toByteIterable() {
        final String json = JsonUtil.serialize(this);
        return StringBinding.stringToEntry(json);
    }

    public boolean isDirectory() {
        return Type.DIR.test(mode);
    }

    public boolean isFile() {
        return Type.FILE.test(mode);
    }

    public boolean isLink() {
        return Type.LINK.test(mode);
    }
}

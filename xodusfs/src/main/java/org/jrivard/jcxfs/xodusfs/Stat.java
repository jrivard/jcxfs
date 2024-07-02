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

import java.util.function.IntPredicate;

interface Stat {
    int S_IFMT = 61440;
    int S_IFSOCK = 49152;
    int S_IFLNK = 40960;
    int S_IFREG = 32768;
    int S_IFBLK = 24576;
    int S_IFDIR = 16384;
    int S_IFCHR = 8192;
    int S_IFIFO = 4096;
    IntPredicate S_ISREG = (m) -> {
        return (m & '\uf000') == 32768;
    };
    IntPredicate S_ISDIR = (m) -> {
        return (m & '\uf000') == 16384;
    };
    IntPredicate S_ISCHR = (m) -> {
        return (m & '\uf000') == 8192;
    };
    IntPredicate S_ISBLK = (m) -> {
        return (m & '\uf000') == 24576;
    };
    IntPredicate S_ISFIFO = (m) -> {
        return (m & '\uf000') == 4096;
    };
    IntPredicate S_ISLNK = (m) -> {
        return (m & '\uf000') == 40960;
    };
    IntPredicate S_ISSOCK = (m) -> {
        return (m & '\uf000') == 49152;
    };

    void setMode(int var1);

    int getMode();

    void setUid(int var1);

    int getUid();

    void setGid(int var1);

    int getGid();

    void setNLink(short var1);

    long getNLink();

    void setSize(long var1);

    long getSize();

    default boolean hasMode(final int mask) {
        return (this.getMode() & mask) == mask;
    }

    default void setModeBits(final int mask) {
        this.setMode(this.getMode() | mask);
    }

    default void unsetModeBits(final int mask) {
        this.setMode(this.getMode() & ~mask);
    }
}

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

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.stream.Stream;
import jetbrains.exodus.env.Transaction;

public interface XodusFs extends Closeable {
    int VERSION = 1;

    long fileLength(String path) throws FileOpException;

    Optional<InodeEntry> readAttrs(String path) throws FileOpException;

    Stream<String> directoryListing(String path) throws FileOpException;

    void createDirectoryEntry(String path, int mode) throws FileOpException;

    void removeDirectoryEntry(String path) throws FileOpException;

    int read(String path, ByteBuffer buf, long count, long offset) throws FileOpException;

    void createFileEntry(String path, int mode) throws FileOpException;

    int writeFileData(String path, ByteBuffer buf, long count, long offset) throws FileOpException;

    @Override
    void close();

    void removeFileEntry(String path) throws FileOpException;

    void updateMtime(Transaction txn, long nodeId) throws FileOpException;

    void rename(String oldPath, String newPath) throws FileOpException;

    StatfsInfo readStatfsInfo() throws FileOpException;

    void truncate(String path, long size) throws FileOpException;

    void writeAttrs(String path, InodeEntry entryAttrs) throws FileOpException;

    void createSymLink(String path, String target) throws FileOpException;

    String readSymLink(String path) throws FileOpException;
}

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

class PathStore implements StoreBucket {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(PathStore.class);

    private final Store pathStore;
    private Store pathCacheStore;
    private final EnvironmentWrapper environmentWrapper;

    public PathStore(final EnvironmentWrapper environmentWrapper) {
        this.environmentWrapper = environmentWrapper;
        pathStore = environmentWrapper.getStore(XodusStore.PATH);
        pathCacheStore = environmentWrapper.getStore(XodusStore.PATH_CACHE);
    }

    private void validatePathKeyForWrite(final PathKey pathKey) {
        if (pathKey.isRoot()) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "can not modify root path");
        }
    }

    public long readEntry(final Transaction txn, final PathKey path) {
        final ByteIterable cachedValue = pathCacheStore.get(txn, path.toByteIterable());
        if (cachedValue != null) {
            return InodeId.byteIterableToInodeId(cachedValue);
        }
        final long readValue = readEntryImpl(txn, path);
        if (readValue > 0) {
            pathCacheStore.put(txn, path.toByteIterable(), InodeId.inodeIdToByteIterable(readValue));
            LOGGER.debug(() ->
                    "created db-cache entry for path '" + path.path() + "', id=" + InodeId.prettyPrint(readValue));
        }
        return readValue;
    }

    private long readEntryImpl(final Transaction txn, final PathKey path) {
        if (path.isRoot()) {
            return InodeId.ROOT_INODE;
        }

        // first parent is always root.
        long segmentId = InodeId.ROOT_INODE;

        final List<String> segments = path.segments();

        for (final String segment : segments) {
            try (final Stream<PathRecord> pathRecordStream = readRecordsForId(txn, segmentId)) {
                final Optional<PathRecord> segmentRecord = pathRecordStream
                        .filter(record -> record.name().equals(segment))
                        .findFirst();
                if (segmentRecord.isEmpty()) {
                    return -1;
                }

                segmentId = segmentRecord.get().id();
            }
        }

        return segmentId;
    }

    public void createEntry(final Transaction txn, final PathKey path, final long inodeId) {
        validatePathKeyForWrite(path);

        if (readEntry(txn, path) > 0) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "path already exists exist");
        }

        final PathKey parentPath = path.parent();
        final long parentId = readEntry(txn, parentPath);
        if (parentId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent path does not exist");
        }

        final PathRecord pathRecord = new PathRecord(inodeId, path.suffix());
        pathStore.put(txn, InodeId.inodeIdToByteIterable(parentId), pathRecord.toByteIterable());
    }

    public void removeEntry(final Transaction txn, final PathKey path) {
        removeEntryImpl(txn, path, true);
    }

    private void removeEntryImpl(final Transaction txn, final PathKey path, final boolean checkForChildren) {
        validatePathKeyForWrite(path);
        final long pathId = readEntry(txn, path);
        if (pathId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "path does not exist");
        }
        try (final Stream<String> subPathStream = readSubPaths(txn, path)) {
            if (checkForChildren && subPathStream.findAny().isPresent()) {
                throw RuntimeXodusFsException.of(FileOpError.DIR_NOT_EMPTY, "path has descendants");
            }
        }
        final long parentId = readEntry(txn, path.parent());
        final ByteIterable parentKey = InodeId.inodeIdToByteIterable(parentId);
        final PathRecord pathRecord = new PathRecord(pathId, path.suffix());
        pathCacheStore.delete(txn, path.toByteIterable());
        try {
            environmentWrapper.removeKeyValue(txn, XodusStore.PATH, parentKey, pathRecord.toByteIterable());
        } catch (final FileOpException e) {
            throw RuntimeXodusFsException.of(e.getError(), e.getMessage());
        }
    }

    public Stream<String> readSubPaths(final Transaction txn, final PathKey path) {
        final long nodeId = readEntry(txn, path);
        if (nodeId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "path does not exist");
        }
        return readRecordsForId(txn, nodeId).map(PathRecord::name);
    }

    private Stream<PathRecord> readRecordsForId(final Transaction txn, final long id) {
        return environmentWrapper
                .allEntriesForKey(txn, XodusStore.PATH, InodeId.inodeIdToByteIterable(id))
                .map(entry -> PathRecord.fromByteIterable(entry.getValue()));
    }

    public void rename(final Transaction txn, final PathKey oldPath, final PathKey newPath) {
        validatePathKeyForWrite(oldPath);
        validatePathKeyForWrite(newPath);
        final long oldPathId = readEntry(txn, oldPath);
        if (oldPathId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "oldPath does not exist");
        }

        final long newPathId = readEntry(txn, newPath);
        if (newPathId > 0) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "newPath already exists");
        }

        final long newParentId = readEntry(txn, newPath.parent());
        if (newParentId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent of new path does not exist");
        }

        final boolean invalidatesCache;
        try (final Stream<String> subPathStream = readSubPaths(txn, oldPath)) {
            invalidatesCache = subPathStream.findAny().isPresent();
        }

        removeEntryImpl(txn, oldPath, false);
        createEntry(txn, newPath, oldPathId);
        if (invalidatesCache) {
            final long cacheCount = pathCacheStore.count(txn);
            environmentWrapper.truncateStore(txn, XodusStore.PATH_CACHE);
            pathCacheStore = environmentWrapper.getStore(XodusStore.PATH_CACHE);
            LOGGER.debug(() -> "purged " + cacheCount + " records from db-cache");
        }
    }

    private class DebugOutputter {

        private final Transaction transaction;
        private final XodusDebugWriter output;

        public DebugOutputter(final Transaction transaction, final XodusDebugWriter output) {
            this.transaction = transaction;
            this.output = output;
        }

        public void printStats() {
            environmentWrapper.forEach(transaction, XodusStore.PATH, byteIterableByteIterableEntry -> {
                final Map.Entry<Long, PathRecord> parsedEntry = convertPathMapEntry(byteIterableByteIterableEntry);
                printPathEntryDebug(parsedEntry, output);
            });
        }

        private void printPathEntryDebug(final Map.Entry<Long, PathRecord> entry, final XodusDebugWriter writer) {
            final long id = entry.getKey();
            final PathRecord pathRecord = entry.getValue();
            writer.writeLine(" id=" + InodeId.prettyPrint(id) + " child record: id="
                    + InodeId.prettyPrint(pathRecord.id()) + " name='" + pathRecord.name() + "'");

            /*try {
                       environmentWrapper.doExecute(txn -> {
                           try (final Stream<String> subpathStream = readSubPaths(txn, pathKey)) {
                               subpathStream.forEach(subPath -> {
                                   writer.writeLine("   subpath: " + subPath);
                               });
                           }
                       });
                   } catch (final FileOpException e) {
                       throw new RuntimeException(e);
                   }
            */ }

        static Map.Entry<Long, PathRecord> convertPathMapEntry(final Map.Entry<ByteIterable, ByteIterable> entry) {
            return Map.entry(
                    InodeId.byteIterableToInodeId(entry.getValue()), PathRecord.fromByteIterable(entry.getKey()));
        }
    }

    public void printStats(final XodusDebugWriter writer) {

        try {
            environmentWrapper.doExecute(txn -> {
                new DebugOutputter(txn, writer).printStats();
            });
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }
}

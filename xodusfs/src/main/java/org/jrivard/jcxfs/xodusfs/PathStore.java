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

import com.github.benmanes.caffeine.cache.Cache;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.xodusfs.util.StatCounterBundle;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

class PathStore implements StoreBucket {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(PathStore.class);

    private final Store pathStore;
    private final EnvironmentWrapper environmentWrapper;
    private final Cache<String, Long> pathCache;

    private final StatCounterBundle<PathStoreDebugStats> stats = new StatCounterBundle<>(PathStoreDebugStats.class);

    public enum PathStoreDebugStats {
        pathRecordCreates,
        pathRecordDeletes,
        pathRecordRenames,
        pathRecordReads,
    }

    public PathStore(final EnvironmentWrapper environmentWrapper) {
        this.environmentWrapper = environmentWrapper;
        pathStore = environmentWrapper.getStore(XodusStore.PATH);
        pathCache = StoreBucket.makeCache(environmentWrapper);
    }

    private void validatePathKeyForWrite(final PathKey pathKey) {
        if (pathKey.isRoot()) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "can not modify root path");
        }
    }

    public long readEntry(final Transaction txn, final PathKey path) {
        final Long cachedValue = pathCache.getIfPresent(path.path());
        if (cachedValue != null) {
            return cachedValue;
        }
        final long readValue = readEntryImpl(txn, path);
        if (readValue > 0) {
            pathCache.put(path.path(), readValue);
            LOGGER.trace(() ->
                    "created db-cache entry for path '" + path.path() + "', id=" + InodeId.prettyPrint(readValue));
        }
        stats.increment(PathStoreDebugStats.pathRecordReads);
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

    @Override
    public void close() {}

    public void createEntry(final Transaction txn, final PathKey path, final long inodeId) {

        validatePathKeyForWrite(path);

        if (readEntry(txn, path) > 0) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "path already exists exist");
        }

        final long parentId = readEntry(txn, path.parent());
        if (parentId <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent path does not exist");
        }

        final PathRecord pathRecord = new PathRecord(inodeId, path.suffix());
        pathStore.put(txn, InodeId.inodeIdToByteIterable(parentId), pathRecord.toByteIterable());
        stats.increment(PathStoreDebugStats.pathRecordCreates);
    }

    public long size(final Transaction txn) {
        return pathStore.count(txn);
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
        pathCache.invalidate(path.path());
        try {
            final boolean removed =
                    environmentWrapper.removeKeyValue(txn, XodusStore.PATH, parentKey, pathRecord.toByteIterable());
            if (!removed) {
                throw RuntimeXodusFsException.of(
                        FileOpError.IO_ERROR, "error removing entry, unable to detach from parent entry");
            }
        } catch (final FileOpException e) {
            throw RuntimeXodusFsException.of(e.getError(), e.getMessage());
        }
        stats.increment(PathStoreDebugStats.pathRecordDeletes);
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
        stats.increment(PathStoreDebugStats.pathRecordRenames);

        if (invalidatesCache) {
            final long cacheCount = pathCache.estimatedSize();
            pathCache.invalidateAll();
            LOGGER.debug(() -> "purged " + cacheCount + " records from db-cache");
        }
    }

    @Override
    public Map<String, String> runtimeStats() {
        return stats.debugStats();
    }

    private class DebugOutputter {

        private final Transaction transaction;
        private final XodusConsoleWriter output;

        public DebugOutputter(final Transaction transaction, final XodusConsoleWriter output) {
            this.transaction = transaction;
            this.output = output;
        }

        public void printStats() {
            output.writeLine("PathStore Dump Output: ");
            environmentWrapper.forEach(transaction, XodusStore.PATH, byteIterableByteIterableEntry -> {
                final Map.Entry<Long, PathRecord> parsedEntry = convertPathMapEntry(byteIterableByteIterableEntry);
                printPathEntryDebug(parsedEntry, output);
                output.writeLine(" key="
                        + HexFormat.of()
                                .formatHex(
                                        byteIterableByteIterableEntry.getKey().getBytesUnsafe())
                        + " value="
                        + HexFormat.of()
                                .formatHex(
                                        byteIterableByteIterableEntry.getValue().getBytesUnsafe()));
            });
        }

        private void printPathEntryDebug(final Map.Entry<Long, PathRecord> entry, final XodusConsoleWriter writer) {
            final long id = entry.getKey();
            final PathRecord pathRecord = entry.getValue();
            writer.writeLine(" id=" + InodeId.prettyPrint(id) + " child record: id="
                    + InodeId.prettyPrint(pathRecord.id()) + " name='" + pathRecord.name() + "'");
        }

        static Map.Entry<Long, PathRecord> convertPathMapEntry(final Map.Entry<ByteIterable, ByteIterable> entry) {
            final long id = InodeId.byteIterableToInodeId(entry.getKey());
            final PathRecord pathRecord = PathRecord.fromByteIterable(entry.getValue());
            return Map.entry(id, pathRecord);
        }
    }

    public void printDumpOutput(final XodusConsoleWriter writer) {

        try {
            environmentWrapper.doExecute(txn -> {
                new DebugOutputter(txn, writer).printStats();
            });
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }
}

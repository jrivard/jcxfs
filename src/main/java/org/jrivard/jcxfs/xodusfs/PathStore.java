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
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.util.JcxfsOutput;

class PathStore implements StoreBucket {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(PathStore.class);

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
            final Optional<PathRecord> segmentRecord = readRecordsForId(txn, segmentId)
                    .filter(record -> record.name().equals(segment))
                    .findFirst();
            if (segmentRecord.isEmpty()) {
                return -1;
            }

            segmentId = segmentRecord.get().id();
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
        if (checkForChildren && readSubPaths(txn, path).findAny().isPresent()) {
            throw RuntimeXodusFsException.of(FileOpError.DIR_NOT_EMPTY, "path has descendants");
        }
        final long parentId = readEntry(txn, path.parent());
        final ByteIterable parentKey = InodeId.inodeIdToByteIterable(parentId);
        final PathRecord pathRecord = new PathRecord(pathId, path.suffix());
        pathCacheStore.delete(txn, path.toByteIterable());
        environmentWrapper.removeKeyValue(txn, XodusStore.PATH, parentKey, pathRecord.toByteIterable());
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

        final boolean invalidatesCache = readSubPaths(txn, oldPath).findAny().isPresent();

        removeEntryImpl(txn, oldPath, false);
        createEntry(txn, newPath, oldPathId);
        if (invalidatesCache) {
            environmentWrapper.truncateStore(txn, XodusStore.PATH_CACHE);
            pathCacheStore = environmentWrapper.getStore(XodusStore.PATH_CACHE);
        }
    }

    private class DebugOutputter {

        private final Transaction transaction;
        private final JcxfsOutput output;

        public DebugOutputter(final Transaction transaction, final JcxfsOutput output) {
            this.transaction = transaction;
            this.output = output;
        }

        public void printStats() {

            environmentWrapper
                    .allEntries(transaction, XodusStore.PATH)
                    .map(DebugOutputter::convertPathMapEntry)
                    .forEach(entry -> printPathEntryDebug(entry, output));
        }

        private void printPathEntryDebug(final Map.Entry<PathKey, Long> entry, final JcxfsOutput writer) {
            final PathKey pathKey = entry.getKey();
            writer.writeLine(" path: " + pathKey.toString() + " inode=" + InodeId.prettyPrint(entry.getValue()));
            try {
                environmentWrapper.doExecute(txn -> {
                    readSubPaths(txn, pathKey).forEach(subPath -> {
                        writer.writeLine("   subpath: " + subPath);
                    });
                });
            } catch (final FileOpException e) {
                throw new RuntimeException(e);
            }
        }

        static Map.Entry<PathKey, Long> convertPathMapEntry(final Map.Entry<ByteIterable, ByteIterable> entry) {
            return Map.entry(PathKey.fromByteIterable(entry.getKey()), InodeId.byteIterableToInodeId(entry.getValue()));
        }
    }

    public void printStats(final JcxfsOutput writer) {

        try {
            environmentWrapper.doExecute(txn -> {
                new DebugOutputter(txn, writer).printStats();
            });
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }
}

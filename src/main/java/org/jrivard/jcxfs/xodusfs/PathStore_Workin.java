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

import java.util.Map;
import java.util.stream.Stream;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.JcxfsException;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.util.JcxfsOutput;

class PathStore_Workin implements StoreBucket {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(PathStore_Workin.class);

    private final Store pathStore;
    private final Store subPathStore;
    private final EnvironmentWrapper environmentWrapper;

    public PathStore_Workin(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        this.environmentWrapper = environmentWrapper;
        pathStore = environmentWrapper.getStore(XodusStore.PATH);
        subPathStore = environmentWrapper.getStore(XodusStore.PATH_CACHE);

        createRootEntry();
    }

    private void createRootEntry() throws JcxfsException {
        try {
            environmentWrapper.doExecute(txn -> {
                if (pathStore.get(txn, PathKey.root().toByteIterable()) == null) {
                    final ByteIterable rootInodeValue = InodeId.rootId();
                    pathStore.put(txn, PathKey.root().toByteIterable(), rootInodeValue);
                }
                LOGGER.debug("created root path");
            });
        } catch (final FileOpException e) {
            throw new JcxfsException("unable to create root path");
        }
    }

    private void validatePathKeyForWrite(final PathKey pathKey) {
        if (pathKey.isRoot()) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "can not modify root path");
        }
    }

    public long readEntry(final Transaction txn, final PathKey path) {
        final ByteIterable byteIterable = pathStore.get(txn, path.toByteIterable());
        if (byteIterable != null) {
            return InodeId.byteIterableToInodeId(byteIterable);
        }
        return -1;
    }

    public void createEntry(final Transaction txn, final PathKey path, final long inodeId) {
        validatePathKeyForWrite(path);

        if (readEntry(txn, path) > 0) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "pathalready exists exist");
        }

        final PathKey parentPath = path.parent();
        if (readEntry(txn, parentPath) <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent path does not exist");
        }

        pathStore.put(txn, path.toByteIterable(), InodeId.inodeIdToByteIterable(inodeId));
        addSubPath(txn, parentPath, path.suffix());
    }

    public void removeEntry(final Transaction txn, final PathKey path) {
        validatePathKeyForWrite(path);
        if (readEntry(txn, path) <= 0) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "path does not exist");
        }
        if (readSubPaths(txn, path).findAny().isPresent()) {
            throw RuntimeXodusFsException.of(FileOpError.DIR_NOT_EMPTY, "path has descendants");
        }
        pathStore.delete(txn, path.toByteIterable());
        removeSubPath(txn, path.parent(), path.suffix());
    }

    public Stream<String> readSubPaths(final Transaction txn, final PathKey path) {
        final ByteIterable nodeId = pathStore.get(txn, path.toByteIterable());
        if (nodeId == null) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "path does not exist");
        }
        return environmentWrapper
                .allEntriesForKey(txn, XodusStore.PATH_CACHE, nodeId)
                .map(entry -> StringBinding.entryToString(entry.getValue()));
    }

    private void addSubPath(final Transaction txn, final PathKey path, final String subPath) {
        final ByteIterable nodeId = pathStore.get(txn, path.toByteIterable());
        if (nodeId == null) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "path does not exist");
        }
        subPathStore.put(txn, nodeId, StringBinding.stringToEntry(subPath));
    }

    private void removeSubPath(final Transaction txn, final PathKey path, final String subPath) {
        final ByteIterable nodeId = pathStore.get(txn, path.toByteIterable());
        if (nodeId == null) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "path does not exist");
        }
        final ByteIterable value = StringBinding.stringToEntry(subPath);
        try (final Cursor cursor = subPathStore.openCursor(txn)) {
            if (cursor.getSearchBoth(nodeId, value)) {
                cursor.deleteCurrent();
            }
        }
    }

    public void rename(final Transaction txn, final PathKey oldPath, final PathKey newPath) {
        final ByteIterable oldPathKey = oldPath.toByteIterable();
        final ByteIterable id = pathStore.get(txn, oldPathKey);
        if (id == null) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "oldPath does not exist");
        }

        final ByteIterable newPathKey = newPath.toByteIterable();
        if (pathStore.get(txn, newPathKey) != null) {
            throw RuntimeXodusFsException.of(FileOpError.FILE_EXISTS, "newPath already exists");
        }

        final PathKey newParentKey = newPath.parent();
        if (!newPath.isRoot() && pathStore.get(txn, newParentKey.toByteIterable()) == null) {
            throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent of new path does not exist");
        }

        pathStore.delete(txn, oldPathKey);
        pathStore.put(txn, newPathKey, id);
        removeSubPath(txn, oldPath.parent(), oldPath.suffix());
        addSubPath(txn, newParentKey, newPath.suffix());
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

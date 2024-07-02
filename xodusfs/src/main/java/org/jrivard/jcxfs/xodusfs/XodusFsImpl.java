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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

class XodusFsImpl implements XodusFs {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(XodusFsImpl.class);

    private final EnvironmentWrapper ew;
    private final PathStore pathStore;
    private final InodeStore inodeStore;
    private final DataStore dataStore;
    private final XodusFsParams xodusFsParams;

    private XodusFsImpl(
            final EnvironmentWrapper ew,
            final PathStore pathStore,
            final InodeStore inodeStore,
            final DataStore dataStore,
            final XodusFsParams xodusFsParams) {
        this.ew = ew;
        this.pathStore = pathStore;
        this.inodeStore = inodeStore;
        this.dataStore = dataStore;
        this.xodusFsParams = xodusFsParams;

        LOGGER.debug(() -> "opened db with params: " + xodusFsParams);
    }

    static XodusFsImpl createXodusFsImpl(
            final EnvironmentWrapper ew,
            final PathStore pathStore,
            final InodeStore inodeStore,
            final DataStore dataStore,
            final XodusFsParams xodusFsParams) {
        return new XodusFsImpl(ew, pathStore, inodeStore, dataStore, xodusFsParams);
    }

    public List<StoreBucket> storeBuckets() {
        return List.of(pathStore, inodeStore, dataStore);
    }

    @Override
    public long fileLength(final String path) throws FileOpException {
        return ew.doCompute(txn -> {
            {
                final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
                if (nodeId > 0) {
                    return dataStore.length(txn, nodeId);
                }
                return -1L;
            }
        });
    }

    @Override
    public Optional<InodeEntry> readAttrs(final String path) throws FileOpException {
        return ew.doCompute(txn -> {
            {
                final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
                if (nodeId > 0) {
                    return inodeStore.readEntry(txn, nodeId);
                }
            }

            return Optional.empty();
        });
    }

    @Override
    public Stream<String> directoryListing(final String path) throws FileOpException {
        return ew.doCompute(txn -> pathStore.readSubPaths(txn, PathKey.of(path)));
    }

    @Override
    public void createDirectoryEntry(final String path, final int mode) throws FileOpException {
        createEntryImpl(path, InodeEntry.newDirectoryEntry(mode));
    }

    @Override
    public void removeDirectoryEntry(final String path) throws FileOpException {
        final PathKey pathKey = PathKey.of(path);
        ew.doExecute(txn -> {
            final long parentNodeId = pathStore.readEntry(txn, pathKey.parent());
            if (parentNodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent directory does not exist");
            }

            final InodeEntry inodeEntry = inodeStore
                    .readEntry(txn, parentNodeId)
                    .orElseThrow(() -> new IllegalStateException("missing inode entry for parent path"));

            if (!inodeEntry.isDirectory()) {
                throw RuntimeXodusFsException.of(FileOpError.NOT_A_DIRECTORY, "parent path is not a directory");
            }

            final long nodeId = pathStore.readEntry(txn, pathKey);
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "directory does not exist");
            }

            if (pathStore.readSubPaths(txn, pathKey).findAny().isPresent()) {
                throw RuntimeXodusFsException.of(FileOpError.DIR_NOT_EMPTY, "directory not empty");
            }

            pathStore.removeEntry(txn, pathKey);
            inodeStore.removeEntry(txn, nodeId);
            updateMtime(txn, parentNodeId);
        });
    }

    @Override
    public int read(final String path, final ByteBuffer buf, final long count, final long offset)
            throws FileOpException {
        return ew.doCompute(txn -> {
            final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "file does not exist");
            }

            final InodeEntry inodeEntry = inodeStore
                    .readEntry(txn, nodeId)
                    .orElseThrow(() -> RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "no such file"));

            if (!inodeEntry.isFile()) {
                throw RuntimeXodusFsException.of(FileOpError.NOT_A_FILE, "path is not a file");
            }

            return dataStore.readData(txn, nodeId, buf, count, offset);
        });
    }

    @Override
    public void createFileEntry(final String path, final int mode) throws FileOpException {
        createEntryImpl(path, InodeEntry.newFileEntry(mode));
    }

    private void createEntryImpl(final String path, final InodeEntry newEntry) throws FileOpException {
        final PathKey pathKey = PathKey.of(path);
        ew.doExecute(txn -> {
            final long parentNodeId = pathStore.readEntry(txn, pathKey.parent());
            final InodeEntry parentEntry = inodeStore
                    .readEntry(txn, parentNodeId)
                    .orElseThrow(() -> new IllegalStateException("missing inode entry for path"));

            if (!parentEntry.isDirectory()) {
                throw RuntimeXodusFsException.of(FileOpError.NOT_A_DIRECTORY, "parent path is not a directory");
            }

            final long newId = inodeStore.issuer().nextId(txn);
            pathStore.createEntry(txn, pathKey, newId);
            inodeStore.createEntry(txn, newId, newEntry);
            inodeStore.updateEntry(txn, parentNodeId, parentEntry.withMtimeNow());
        });
    }

    @Override
    public int writeFileData(final String path, final ByteBuffer buf, final long count, final long offset)
            throws FileOpException {
        return ew.doCompute(txn -> {
            final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "file does not exist");
            }

            final InodeEntry inodeEntry = inodeStore
                    .readEntry(txn, nodeId)
                    .orElseThrow(() -> RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "no such file"));

            if (!inodeEntry.isFile()) {
                throw RuntimeXodusFsException.of(FileOpError.NOT_A_FILE, "path is not a file");
            }

            final int bytesWritten = dataStore.writeData(txn, nodeId, buf, count, offset);
            final InodeEntry newInodeEntry = inodeEntry.withMtimeNow();
            inodeStore.updateEntry(txn, nodeId, newInodeEntry);
            return bytesWritten;
        });
    }

    @Override
    public void close() {
        this.ew.close();
    }

    @Override
    public void removeFileEntry(final String path) throws FileOpException {
        final PathKey pathKey = PathKey.of(path);
        ew.doExecute(txn -> {
            final long nodeId = pathStore.readEntry(txn, pathKey);
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "file does not exist");
            }

            final long parentNodeId = pathStore.readEntry(txn, pathKey.parent());
            if (parentNodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_DIR, "parent directory does not exist");
            }

            final InodeEntry inodeEntry = inodeStore
                    .readEntry(txn, nodeId)
                    .orElseThrow(() -> RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "no such file"));

            if (!inodeEntry.isFile() && !inodeEntry.isLink()) {
                throw RuntimeXodusFsException.of(FileOpError.NOT_A_FILE, "path is not a file");
            }

            inodeStore.removeEntry(txn, nodeId);
            pathStore.removeEntry(txn, pathKey);
            updateMtime(txn, parentNodeId);
            dataStore.deleteEntry(txn, nodeId);
        });
    }

    @Override
    public void rename(final String oldPath, final String newPath) throws FileOpException {
        ew.doExecute(txn -> {
            pathStore.rename(txn, PathKey.of(oldPath), PathKey.of(newPath));
        });
    }

    @Override
    public StatfsInfo readStatfsInfo() throws FileOpException {
        final long freeSpace = this.ew.envPath().toFile().getFreeSpace();
        final long pagesUsed = ew.doCompute(dataStore::totalPagesUsed);

        return new StatfsInfo(xodusFsParams.pageSize(), pagesUsed, freeSpace);
    }

    EnvironmentWrapper environmentWrapper() {
        return ew;
    }

    @Override
    public void truncate(final String path, final long size) throws FileOpException {
        ew.doExecute(txn -> {
            final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "file does not exist");
            }

            dataStore.truncate(txn, nodeId, size);
        });
    }

    @Override
    public void updateMtime(final Transaction txn, final long nodeId) {
        final InodeEntry existingEntry = inodeStore
                .readEntry(txn, nodeId)
                .orElseThrow(() -> new IllegalStateException("missing inode entry for path"));
        final InodeEntry newEntry = existingEntry.withMtimeNow();
        inodeStore.updateEntry(txn, nodeId, newEntry);
    }

    @Override
    public void writeAttrs(final String path, final InodeEntry entryAttrs) throws FileOpException {
        ew.doExecute(txn -> {
            {
                final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
                if (nodeId > 0) {
                    inodeStore.updateEntry(txn, nodeId, entryAttrs);
                }
            }
        });
    }

    @Override
    public void createSymLink(final String path, final String target) throws FileOpException {
        createEntryImpl(target, InodeEntry.newLinkEntry().withTargetPath(path));
    }

    @Override
    public String readSymLink(final String path) throws FileOpException {
        return ew.doCompute(txn -> {
            final long nodeId = pathStore.readEntry(txn, PathKey.of(path));
            if (nodeId <= 0) {
                throw RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "file does not exist");
            }

            final InodeEntry inodeEntry = inodeStore
                    .readEntry(txn, nodeId)
                    .orElseThrow(() -> new IllegalStateException("missing inode entry for path"));

            if (!inodeEntry.isLink()) {
                throw RuntimeXodusFsException.of(FileOpError.IO_ERROR, "not a symlink");
            }

            return inodeEntry.targetPath();
        });
    }
}

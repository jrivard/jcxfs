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

import java.util.Optional;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

class InodeStore implements StoreBucket {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(XodusFsLogger.class);

    private final Store inodeStore;
    private final InodeIdIssuer inodeIdIssuer;

    public InodeStore(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        inodeStore = environmentWrapper.getStore(XodusStore.INODE);
        inodeIdIssuer = new InodeIdIssuer(environmentWrapper, this);

        createRootEntry(environmentWrapper);
    }

    private void createRootEntry(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        try {
            environmentWrapper.doExecute(txn -> {
                final ByteIterable rootKey = InodeId.rootId();
                if (inodeStore.get(txn, rootKey) == null) {
                    final ByteIterable rootInodeValue =
                            InodeEntry.newDirectoryEntry().toByteIterable();
                    inodeStore.put(txn, rootKey, rootInodeValue);
                    LOGGER.debug("created root inode");
                }
            });
        } catch (final FileOpException e) {
            throw new JcxfsException("unable to create root path");
        }
    }

    InodeIdIssuer issuer() {
        return inodeIdIssuer;
    }

    public void createEntry(final Transaction txn, final long newId, final InodeEntry inodeEntry) {
        updateEntry(txn, newId, inodeEntry);
    }

    public Optional<InodeEntry> readEntry(final Transaction txn, final long id) {
        final ByteIterable byteIterable = inodeStore.get(txn, InodeId.inodeIdToByteIterable(id));
        return Optional.ofNullable(byteIterable).map(InodeEntry::fromByteIterable);
    }

    public void updateEntry(final Transaction txn, final long id, final InodeEntry inodeEntry) {
        final ByteIterable inodeByteIterable = InodeId.inodeIdToByteIterable(id);
        inodeStore.put(txn, inodeByteIterable, inodeEntry.toByteIterable());
    }

    public void removeEntry(final Transaction txn, final long id) {
        readEntry(txn, id)
                .orElseThrow(() -> RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "inode does not exist"));

        inodeStore.delete(txn, InodeId.inodeIdToByteIterable(id));
    }

    public boolean hasId(final Transaction txn, final long nextLong) {
        return inodeStore.get(txn, InodeId.inodeIdToByteIterable(nextLong)) != null;
    }

    @Override
    public void printStats(final XodusDebugWriter writer) {
        writer.writeLine("InodeStore printStats() not implemented");
    }
}

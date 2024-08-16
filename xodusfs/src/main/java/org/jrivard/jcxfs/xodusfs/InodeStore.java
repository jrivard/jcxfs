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
import java.util.Map;
import java.util.Optional;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import org.jrivard.jcxfs.xodusfs.util.StatCounterBundle;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

class InodeStore implements StoreBucket {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(XodusFsLogger.class);

    private final Store inodeStore;
    private final InodeIdIssuer inodeIdIssuer;
    private final EnvironmentWrapper environmentWrapper;

    private final StatCounterBundle<InodeStoreDebugStats> stats = new StatCounterBundle<>(InodeStoreDebugStats.class);

    private final Cache<Long, Optional<InodeEntry>> inodeCache;

    public enum InodeStoreDebugStats {
        inodeRecordCreates,
        inodeRecordDeletes,
        inodeRecordUpdates,
        inodeRecordReads,
        inodeIdCreateDupes,
    }

    public InodeStore(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        this.environmentWrapper = environmentWrapper;

        inodeStore = environmentWrapper.getStore(XodusStore.INODE);
        inodeIdIssuer = new InodeIdIssuer(environmentWrapper, this, stats);

        inodeCache = StoreBucket.makeCache(environmentWrapper);

        createRootEntry(environmentWrapper);
    }

    private void createRootEntry(final EnvironmentWrapper environmentWrapper) throws JcxfsException {
        if (environmentWrapper.runtimeParameters().readonly()) {
            return;
        }

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

    public long size(final Transaction txn) {
        return inodeStore.count(txn);
    }

    InodeIdIssuer issuer() {
        return inodeIdIssuer;
    }

    @Override
    public void close() {}

    public void createEntry(final Transaction txn, final long newId, final InodeEntry inodeEntry) {
        updateEntry(txn, newId, inodeEntry);
        stats.increment(InodeStoreDebugStats.inodeRecordCreates);
    }

    public Optional<InodeEntry> readEntry(final Transaction txn, final long fid) {
        return inodeCache.get(fid, lambdaFid -> {
            final ByteIterable byteIterable = inodeStore.get(txn, InodeId.inodeIdToByteIterable(lambdaFid));
            stats.increment(InodeStoreDebugStats.inodeRecordReads);
            return Optional.ofNullable(byteIterable).map(InodeEntry::fromByteIterable);
        });
    }

    public void updateEntry(final Transaction txn, final long fid, final InodeEntry inodeEntry) {
        final ByteIterable inodeByteIterable = InodeId.inodeIdToByteIterable(fid);
        inodeCache.invalidate(fid);
        inodeStore.put(txn, inodeByteIterable, inodeEntry.toByteIterable());
        stats.increment(InodeStoreDebugStats.inodeRecordUpdates);
    }

    public void removeEntry(final Transaction txn, final long fid) {
        readEntry(txn, fid)
                .orElseThrow(() -> RuntimeXodusFsException.of(FileOpError.NO_SUCH_FILE, "inode does not exist"));

        inodeCache.invalidate(fid);
        inodeStore.delete(txn, InodeId.inodeIdToByteIterable(fid));
        stats.increment(InodeStoreDebugStats.inodeRecordDeletes);
    }

    public boolean hasId(final Transaction txn, final long nextLong) {
        return readEntry(txn, nextLong).isPresent();
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
            environmentWrapper.forEach(
                    transaction, XodusStore.DATA, byteIterableEntry -> printPathEntryDebug(byteIterableEntry, output));
        }

        private void printPathEntryDebug(
                final Map.Entry<ByteIterable, ByteIterable> entry, final XodusConsoleWriter writer) {

            final long id = InodeId.byteIterableToInodeId(entry.getKey());
            final InodeEntry inodeEntry = InodeEntry.fromByteIterable(entry.getValue());
            writer.writeLine(" inode: id=" + InodeId.prettyPrint(id)
                    + " type=" + inodeEntry.type().map(Enum::name).orElse("unknown")
                    + " entry: " + inodeEntry);
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

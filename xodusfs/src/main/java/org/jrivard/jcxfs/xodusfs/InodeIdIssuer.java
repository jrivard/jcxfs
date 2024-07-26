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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;

class InodeIdIssuer {
    private static final ByteIterable ID_COUNTER = StringBinding.stringToEntry("ID_COUNTER");
    private static final long ID_MIN = Integer.MAX_VALUE;
    private static final long ID_MAX = Long.MAX_VALUE - 10;

    private final Lock CREATE_LOCK = new ReentrantLock();

    private final InodeStore inodeStore;
    private final Store inodeMetaStore;
    private final AtomicLong memoryCounter;

    public InodeIdIssuer(final EnvironmentWrapper environment, final InodeStore inodeStore) throws JcxfsException {
        this.inodeStore = inodeStore;
        this.inodeMetaStore = environment.getStore(XodusStore.INODE_META);
        try {
            memoryCounter = environment.doCompute(txn -> {
                final ByteIterable storedValue = inodeMetaStore.get(txn, ID_COUNTER);
                if (storedValue != null) {
                    final long storedLong = InodeId.byteIterableToInodeId(storedValue);
                    return new AtomicLong(storedLong);
                } else {
                    return new AtomicLong(ID_MIN);
                }
            });
        } catch (final FileOpException e) {
            throw new JcxfsException("error initializing inode-id-issuer: " + e.getError(), e);
        }
    }

    public long nextId(final Transaction txn) {
        CREATE_LOCK.lock();
        long safetyCounter = 0;
        try {
            for (; safetyCounter < ID_MAX; safetyCounter++) {
                final long nextLong = next();
                if (!inodeStore.hasId(txn, nextLong)) {
                    final ByteIterable nextAsByteIterable = InodeId.inodeIdToByteIterable(nextLong);
                    inodeMetaStore.put(txn, ID_COUNTER, nextAsByteIterable);
                    return nextLong;
                }
            }
        } finally {
            CREATE_LOCK.unlock();
        }
        throw new IllegalStateException("unable to create new inode after " + safetyCounter + " searches");
    }

    private long next() {
        return memoryCounter.getAndUpdate(operand -> {
            long local = operand + 1;
            if (local >= ID_MAX) {
                local = ID_MIN;
            }
            return local;
        });
    }
}

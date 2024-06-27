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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.env.TransactionalComputable;
import jetbrains.exodus.env.TransactionalExecutable;
import org.jrivard.jcxfs.JcxfsException;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.JsonUtil;

public class EnvironmentWrapper {
    private static final JcxfsLogger LOG = JcxfsLogger.getLogger(EnvironmentWrapper.class);

    private static final ByteIterable KEY_XODUS_FS_PARAMS = StringBinding.stringToEntry("XODUS_FS_PARAMS");

    private final Environment environment;
    private final XodusFsConfig config;
    private final EnvParams envParams;

    private final EnumMap<XodusStore, Store> storeCache = new EnumMap<>(XodusStore.class);

    private EnvironmentWrapper(final Environment environment, final XodusFsConfig config, final EnvParams envParams) {
        this.environment = environment;
        this.config = config;
        this.envParams = envParams;

        initStoreCache();
    }

    private void initStoreCache() {
        environment.executeInTransaction(txn -> {
            Arrays.stream(XodusStore.values()).forEach(store -> initStoreCache(txn, store));
        });
    }

    private void initStoreCache(final Transaction txn, final XodusStore storeName) {
        final Store store = environment.openStore(storeName.name(), storeName.getStoreConfig(), txn);
        storeCache.put(storeName, store);
    }

    public EnvParams envParams() {
        return envParams;
    }

    public Path envPath() {
        return config.path();
    }

    Store getStore(final XodusStore storeName) {
        return storeCache.get(storeName);
    }

    public void truncateAllStores() {
        environment.executeInTransaction(txn -> {
            for (final String key : environment.getAllStoreNames(txn)) {
                if (environment.storeExists(key, txn)) {
                    environment.truncateStore(key, txn);
                }
            }
        });
        initStoreCache();
    }

    public void truncateStore(final Transaction txn, final XodusStore xodusStore) {
        if (environment.storeExists(xodusStore.name(), txn)) {
            environment.truncateStore(xodusStore.name(), txn);
        }
        initStoreCache(txn, xodusStore);
    }

    public void close() {
        this.environment.close();
    }

    public static EnvironmentWrapper forConfig(final XodusFsConfig config) throws JcxfsException {
        final EnvParams envParams = EnvParams.readFromFile(config.path());
        return EnvironmentWrapper.forConfig(config, envParams);
    }

    public static EnvironmentWrapper forConfig(final XodusFsConfig config, final EnvParams envParams)
            throws JcxfsException {
        return new EnvironmentWrapper(XodusFsUtils.openEnv(config), config, envParams);
    }

    <R> R doCompute(final TransactionalComputable<R> computable) throws FileOpException {
        try {
            return environment.computeInTransaction(computable);
        } catch (final RuntimeXodusFsException e) {
            throw e.asXodusFsException();
        }
    }

    public void doExecute(final TransactionalExecutable executable) throws FileOpException {
        try {
            environment.executeInTransaction(executable);
        } catch (final RuntimeXodusFsException e) {
            throw e.asXodusFsException();
        }
    }

    public boolean removeKeyValue(
            final Transaction txn, final XodusStore xodusStore, final ByteIterable key, final ByteIterable value) {
        try (final Cursor cursor = getStore(xodusStore).openCursor(txn)) {
            if (cursor.getSearchBoth(key, value)) {
                cursor.deleteCurrent();
                return true;
            }
        }
        return false;
    }

    public Stream<Map.Entry<ByteIterable, ByteIterable>> allEntries(final Transaction txn, final XodusStore store) {
        final InnerIterator innerIterator = new InnerIterator(txn, store, null);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(innerIterator, 0), false)
                .onClose(innerIterator::close);
    }

    public Stream<Map.Entry<ByteIterable, ByteIterable>> allEntriesForKey(
            final Transaction txn, final XodusStore store, final ByteIterable key) {
        final InnerIterator innerIterator = new InnerIterator(txn, store, key);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(innerIterator, 0), false)
                .onClose(innerIterator::close);
    }

    private class InnerIterator implements Iterator<Map.Entry<ByteIterable, ByteIterable>>, AutoCloseable {
        private final Transaction transaction;
        private final Cursor cursor;
        private final ByteIterable selectedKey;

        private boolean closed;
        private boolean firstSearch;

        private Map.Entry<ByteIterable, ByteIterable> nextValue = null;

        InnerIterator(final Transaction transaction, final XodusStore store, final ByteIterable selectedKey) {
            this.transaction = transaction;
            this.cursor = getStore(store).openCursor(transaction);
            this.selectedKey = selectedKey;
            doNext();
        }

        private void doNext() {
            try {
                if (closed) {
                    return;
                }

                if (selectedKey == null) {
                    if (!cursor.getNext()) {
                        close();
                        return;
                    }
                } else {
                    if (!firstSearch) {
                        cursor.getSearchKey(selectedKey);
                        firstSearch = true;
                    } else {
                        if (!cursor.getNextDup()) {
                            close();
                            return;
                        }
                    }
                }

                final ByteIterable nextCursor = cursor.getKey();
                if (nextCursor == null || nextCursor.getLength() == 0) {
                    close();
                    return;
                }
                final ByteIterable nextValueIterable = cursor.getValue();

                nextValue = Map.entry(nextCursor, nextValueIterable);
            } catch (final Exception e) {
                throw e;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            cursor.close();
            nextValue = null;
            closed = true;
        }

        @Override
        public boolean hasNext() {
            return !closed && nextValue != null;
        }

        @Override
        public Map.Entry<ByteIterable, ByteIterable> next() {
            if (closed) {
                return null;
            }
            final Map.Entry<ByteIterable, ByteIterable> value = nextValue;
            doNext();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }

    public Optional<XodusFsParams> readXodusFsParams() throws JcxfsException {
        try {
            return doCompute(txn -> {
                final ByteIterable data = getStore(XodusStore.XODUS_META).get(txn, KEY_XODUS_FS_PARAMS);
                if (data != null) {
                    final String jsonValue = StringBinding.entryToString(data);
                    final XodusFsParams xodusFsParams = JsonUtil.deserialize(jsonValue, XodusFsParams.class);
                    return Optional.of(xodusFsParams);
                }
                return Optional.empty();
            });
        } catch (FileOpException e) {
            throw new JcxfsException("unable to read stored xodusFsParams: " + e.getMessage());
        }
    }

    public void writeXodusFsParams(final XodusFsParams xodusFsParams) throws JcxfsException {
        Objects.requireNonNull(xodusFsParams);

        try {
            doExecute(txn -> {
                final String jsonValue = JsonUtil.serialize(xodusFsParams);
                final ByteIterable byteIterableValue = StringBinding.stringToEntry(jsonValue);
                getStore(XodusStore.XODUS_META).put(txn, KEY_XODUS_FS_PARAMS, byteIterableValue);
            });
        } catch (FileOpException e) {
            throw new JcxfsException("unable to read stored xodusFsParams: " + e.getMessage());
        }
    }
}

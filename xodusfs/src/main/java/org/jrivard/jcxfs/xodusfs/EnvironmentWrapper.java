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
import java.time.Instant;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.Store;
import jetbrains.exodus.env.StoreConfig;
import jetbrains.exodus.env.Transaction;
import jetbrains.exodus.env.TransactionalComputable;
import jetbrains.exodus.env.TransactionalExecutable;
import org.jrivard.jcxfs.xodusfs.util.JsonUtil;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;
import org.slf4j.event.Level;

public class EnvironmentWrapper {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(EnvironmentWrapper.class);

    private static final ByteIterable KEY_XODUS_FS_PARAMS = StringBinding.stringToEntry("XODUS_FS_PARAMS");

    private final Environment environment;
    private final RuntimeParameters runtimeParameters;
    private final StoredExternalEnvParams envParams;

    private final AtomicBoolean environmentOpen = new AtomicBoolean(true);
    private final AtomicInteger ACTIVE_OPERATIONS = new AtomicInteger(0);
    private final AtomicInteger OPEN_ITERATORS = new AtomicInteger(0);

    private final Map<XodusStore, Store> storeCache;

    private EnvironmentWrapper(
            final Environment environment,
            final RuntimeParameters runtimeParameters,
            final StoredExternalEnvParams envParams) {
        this.environment = environment;
        this.runtimeParameters = runtimeParameters;
        this.envParams = envParams;

        this.storeCache = makeStoreCache(environment);
        initDebug();
    }

    private void initDebug() {
        if (LOGGER.isLevel(Level.DEBUG)) {
            LOGGER.debug(() -> "opened environment with params: " + envParams);
            try {
                doExecute(txn -> {
                    for (final String storeName : this.environment.getAllStoreNames(txn)) {
                        final Store store = this.environment.openStore(storeName, StoreConfig.USE_EXISTING, txn);
                        final long count = store.count(txn);
                        LOGGER.debug(() -> " existing store '" + storeName + "' with count=" + count);
                    }
                });
            } catch (final Throwable t) {
                LOGGER.error(() -> " error generating store size debug output: " + t.getMessage(), t);
            }
        }
    }

    public RuntimeParameters runtimeParameters() {
        return runtimeParameters;
    }

    private static Map<XodusStore, Store> makeStoreCache(final Environment environment) {
        final var map = new EnumMap<XodusStore, Store>(XodusStore.class);

        environment.executeInTransaction(txn -> {
            for (final XodusStore xodusStore : XodusStore.values()) {
                final Store store = environment.openStore(xodusStore.name(), xodusStore.getStoreConfig(), txn);
                map.put(xodusStore, store);
            }
        });

        return Map.copyOf(map);
    }

    public Path envPath() {
        return runtimeParameters.path();
    }

    Store getStore(final XodusStore storeName) {
        return storeCache.get(storeName);
    }

    public void close() {
        LOGGER.debug(() -> "close requested with "
                + ACTIVE_OPERATIONS.get() + " active operations and "
                + OPEN_ITERATORS.get() + " open iterators");

        environmentOpen.set(false);

        final Instant startTime = Instant.now();

        while (ACTIVE_OPERATIONS.get() + OPEN_ITERATORS.get() > 0) {
            LOGGER.debug(
                    () -> "waiting for "
                            + ACTIVE_OPERATIONS.get() + " active operations and "
                            + OPEN_ITERATORS.get() + " open iterators to complete",
                    startTime);
            try {
                Thread.sleep(5000); // Wait for a short period before checking again
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        this.environment.close();
    }

    public static EnvironmentWrapper forConfig(final RuntimeParameters config) throws JcxfsException {
        final StoredExternalEnvParams envParams = StoredExternalEnvParams.readFromFile(config.path());
        return EnvironmentWrapper.forConfig(config, envParams);
    }

    static EnvironmentWrapper forConfig(final RuntimeParameters config, final StoredExternalEnvParams envParams)
            throws JcxfsException {
        return new EnvironmentWrapper(XodusFsUtils.openEnv(config), config, envParams);
    }

    <R> R doCompute(final TransactionalComputable<R> computable) throws FileOpException {
        if (!environmentOpen.get()) {
            throw new IllegalStateException("cannot initiate operation while environment is closed");
        }

        try {
            ACTIVE_OPERATIONS.incrementAndGet();
            return environment.computeInTransaction(computable);
        } catch (final RuntimeXodusFsException e) {
            LOGGER.debug(() -> "error computing transaction: " + e.getMessage(), e);
            throw e.asXodusFsException();
        } finally {
            ACTIVE_OPERATIONS.decrementAndGet();
        }
    }

    void doExecute(final TransactionalExecutable executable) throws FileOpException {
        doCompute(transaction -> {
            executable.execute(transaction);
            return null;
        });
    }

    public boolean removeKeyValue(
            final Transaction txn, final XodusStore xodusStore, final ByteIterable key, final ByteIterable value)
            throws FileOpException {
        try (final Cursor cursor = getStore(xodusStore).openCursor(txn)) {
            if (cursor.getSearchBoth(key, value)) {
                cursor.deleteCurrent();
                return true;
            }
        }

        return false;
    }

    void forEach(
            final Transaction txn,
            final XodusStore store,
            final Consumer<Map.Entry<ByteIterable, ByteIterable>> action) {
        forEach(txn, store, null, action);
    }

    void forEach(
            final Transaction txn,
            final XodusStore store,
            final ByteIterable matchingKey,
            final Consumer<Map.Entry<ByteIterable, ByteIterable>> action) {
        try (final Stream<Map.Entry<ByteIterable, ByteIterable>> stream = allEntriesForKey(txn, store, matchingKey)) {
            stream.forEach(action);
        }
    }

    public Stream<Map.Entry<ByteIterable, ByteIterable>> allEntriesForKey(
            final Transaction txn, final XodusStore store, final ByteIterable key) {
        OPEN_ITERATORS.incrementAndGet();
        final InnerIterator innerIterator = new InnerIterator(txn, store, key);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(innerIterator, 0), false)
                .onClose(() -> {
                    OPEN_ITERATORS.decrementAndGet();
                    innerIterator.close();
                });
    }

    private class InnerIterator implements Iterator<Map.Entry<ByteIterable, ByteIterable>>, AutoCloseable {
        private final Cursor cursor;
        private final ByteIterable selectedKey;

        private boolean closed;
        private boolean firstSearch;

        private Map.Entry<ByteIterable, ByteIterable> nextValue = null;

        InnerIterator(final Transaction transaction, final XodusStore store, final ByteIterable selectedKey) {
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

    public Optional<StoredInternalEnvParams> readXodusFsParams() throws JcxfsException {
        try {
            return doCompute(txn -> {
                final ByteIterable data = getStore(XodusStore.XODUS_META).get(txn, KEY_XODUS_FS_PARAMS);
                if (data != null) {
                    final String jsonValue = StringBinding.entryToString(data);
                    final StoredInternalEnvParams xodusFsParams =
                            JsonUtil.deserialize(jsonValue, StoredInternalEnvParams.class);
                    return Optional.of(xodusFsParams);
                }
                return Optional.empty();
            });
        } catch (final FileOpException e) {
            throw new JcxfsException("unable to read stored xodusFsParams: " + e.getMessage());
        }
    }

    public void writeXodusFsParams(final StoredInternalEnvParams xodusFsParams) throws JcxfsException {
        Objects.requireNonNull(xodusFsParams);

        try {
            doExecute(txn -> {
                final String jsonValue = JsonUtil.serialize(xodusFsParams);
                final ByteIterable byteIterableValue = StringBinding.stringToEntry(jsonValue);
                getStore(XodusStore.XODUS_META).put(txn, KEY_XODUS_FS_PARAMS, byteIterableValue);
            });
        } catch (final FileOpException e) {
            throw new JcxfsException("unable to read stored xodusFsParams: " + e.getMessage());
        }
    }
}

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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import jetbrains.exodus.crypto.streamciphers.ChaChaStreamCipherProvider;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import org.jetbrains.annotations.NotNull;
import org.jrivard.jcxfs.xodusfs.cipher.ArgonAuthMachine;
import org.jrivard.jcxfs.xodusfs.cipher.AuthException;
import org.jrivard.jcxfs.xodusfs.cipher.AuthMachine;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

public final class XodusFsUtils {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(XodusFsUtils.class);

    public static void printStats(final RuntimeParameters xodusFsConfig, final Writer writer)
            throws FileOpException, JcxfsException {

        final XodusConsoleWriter xodusConsoleWriter = XodusConsoleWriter.forWriter(writer);

        {
            final EnvironmentWrapper environmentWrapper = EnvironmentWrapper.forConfig(xodusFsConfig);
            xodusConsoleWriter.writeLine("db stats:");

            environmentWrapper.doExecute(txn -> {
                for (final XodusStore dataStore : XodusStore.values()) {
                    final Store store = environmentWrapper.getStore(dataStore);
                    final long count = store.count(txn);
                    xodusConsoleWriter.writeLine(dataStore + " records: " + count);
                }
            });

            xodusConsoleWriter.writeLine("\n\nPath Store Output:");
            environmentWrapper.close();
        }

        try (final XodusFsImpl xodusFs = open(xodusFsConfig)) {

            for (final StoreBucket storeBucket : xodusFs.storeBuckets()) {
                storeBucket.printDumpOutput(xodusConsoleWriter);
            }
        } catch (final Exception e) {
            LOGGER.error(() -> "error running printStats: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public record InitParameters(Path path, String password, String cipherClass, String authClass, int pageSize) {
        public static final int DEFAULT_PAGE_SIZE = 4096;
        public static final String DEFAULT_CIPHER_CLASS = ChaChaStreamCipherProvider.class.getName();
        public static final String DEFAULT_AUTH_CLASS = ArgonAuthMachine.class.getName();

        public InitParameters {
            Objects.requireNonNull(path);
            Objects.requireNonNull(password);
            if (password.isEmpty()) {
                throw new IllegalArgumentException("non empty password required");
            }
            cipherClass = cipherClass == null || cipherClass.isEmpty() ? DEFAULT_CIPHER_CLASS : cipherClass;
            authClass = authClass == null || authClass.isEmpty() ? DEFAULT_AUTH_CLASS : authClass;
            pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        }
    }

    public static void initXodusFileStore(final InitParameters initParameters) throws JcxfsException {
        try {
            initImpl(initParameters);
        } catch (final Exception e) {
            throw new JcxfsException("init: io error during init: " + e.getMessage(), e);
        }
    }

    private static void initImpl(final InitParameters initParameters)
            throws JcxfsException, IOException, AuthException {
        if (!Files.exists(initParameters.path())) {
            throw new JcxfsException("init: path does not exist");
        }

        if (!Files.isDirectory(initParameters.path())) {
            throw new JcxfsException("init: path is not a directory");
        }

        try (final Stream<Path> entries = Files.list(initParameters.path())) {
            if (entries.findAny().isPresent()) {
                throw new JcxfsException("init: path must be empty");
            }
        }

        final long iv = RandomGenerator.of("SecureRandom").nextLong();

        final AuthMachine cipherGenerator = AuthMachine.makeInstance(initParameters.authClass());
        cipherGenerator.initNewEnv(initParameters.password());
        final String cipher = cipherGenerator.readCipher(initParameters.password());

        final String passwordData = cipherGenerator.storeEnv();
        final StoredExternalEnvParams envParams =
                new StoredExternalEnvParams(iv, initParameters.cipherClass(), initParameters.authClass(), passwordData);

        envParams.writeToFile(initParameters.path());

        final RuntimeParameters runtimeParameters =
                RuntimeParameters.basic(initParameters.path(), initParameters.password());

        {
            // make new xodus db
            final EnvironmentConfig environmentConfig = makeXodusEnvConfig(runtimeParameters, envParams, cipher);
            final Environment env =
                    Environments.newInstance(initParameters.path().toFile(), environmentConfig);
            env.close();
        }

        final EnvironmentWrapper environmentWrapper = EnvironmentWrapper.forConfig(runtimeParameters);

        final StoredInternalEnvParams internalEnvParams =
                new StoredInternalEnvParams(XodusFs.VERSION, initParameters.pageSize());
        environmentWrapper.writeXodusFsParams(internalEnvParams);
        environmentWrapper.close();
        LOGGER.info(() ->
                "created xodus env at " + initParameters.path() + " with " + initParameters + " and " + envParams);
    }

    static Environment openEnv(final RuntimeParameters config) throws JcxfsException {

        try {
            final StoredExternalEnvParams envParams = StoredExternalEnvParams.readFromFile(config.path());

            final AuthMachine cipherGenerator = AuthMachine.makeInstance(envParams.authClass());
            cipherGenerator.loadEnv(envParams.authData());

            final String cipher = cipherGenerator.readCipher(config.password());

            final EnvironmentConfig environmentConfig = makeXodusEnvConfig(config, envParams, cipher);

            final Environment env = Environments.newInstance(config.path().toFile(), environmentConfig);
            LOGGER.info("opened xodus env at " + config.path());
            return env;
        } catch (final Exception e) {
            final String msg = "error opening xodus database: " + e.getMessage();
            LOGGER.debug(() -> msg, e);
            throw new JcxfsException(msg, e);
        }
    }

    private static @NotNull EnvironmentConfig makeXodusEnvConfig(
            final RuntimeParameters config, final StoredExternalEnvParams envParams, final String cipher) {
        final EnvironmentConfig environmentConfig = new EnvironmentConfig();

        if (config.readonly()) {
            environmentConfig.setEnvIsReadonly(true);
            environmentConfig.setGcEnabled(false);
        } else {
            environmentConfig.setGcMinUtilization(config.gcPercentage());
            environmentConfig.setGcRunEvery(3600);
        }

        environmentConfig.setLogCacheUseNio(true);
        environmentConfig.setLogCacheUseSoftReferences(true);
        environmentConfig.setEnvStoreGetCacheSize(1000);
        environmentConfig.setMemoryUsagePercentage(90);

        environmentConfig.setCipherId(envParams.cipherClass());
        environmentConfig.setCipherKey(cipher);
        environmentConfig.setCipherBasicIV(envParams.iv());
        return environmentConfig;
    }

    public static XodusFsImpl open(final RuntimeParameters xodusFsConfig) throws JcxfsException {
        final EnvironmentWrapper environmentWrapper = EnvironmentWrapper.forConfig(xodusFsConfig);
        return open(environmentWrapper);
    }

    public static void changePassword(final Path path, final String oldPassword, final String newPassword)
            throws JcxfsException, AuthException, IOException {
        final StoredExternalEnvParams storedExternalEnvParams = StoredExternalEnvParams.readFromFile(path);
        final AuthMachine authMachine = AuthMachine.makeInstance(storedExternalEnvParams.authClass());
        authMachine.loadEnv(storedExternalEnvParams.authData());
        authMachine.changePassword(oldPassword, newPassword);
        final String newData = authMachine.storeEnv();
        final StoredExternalEnvParams newExternalEnvParams = new StoredExternalEnvParams(
                storedExternalEnvParams.iv(),
                storedExternalEnvParams.cipherClass(),
                storedExternalEnvParams.authClass(),
                newData);
        newExternalEnvParams.writeToFile(path);
    }

    public static XodusFsImpl open(final EnvironmentWrapper environmentWrapper) throws JcxfsException {

        final var xodusFsParams = environmentWrapper
                .readXodusFsParams()
                .orElseThrow(() -> new JcxfsException("unable to read xodusFsParams from db"));

        if (xodusFsParams.version() != XodusFs.VERSION) {
            throw new JcxfsException("unknown database version '" + xodusFsParams.version() + "'");
        }

        final var inodeStore = new InodeStore(environmentWrapper);
        final var pathStore = new PathStore(environmentWrapper);
        final var dataStore = DataStore.DataStoreImplType.byte_array.makeImpl(environmentWrapper);

        return XodusFsImpl.createXodusFsImpl(environmentWrapper, pathStore, inodeStore, dataStore, xodusFsParams);
    }
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.env.Store;
import org.jrivard.jcxfs.CipherGenerator;
import org.jrivard.jcxfs.JcxfsException;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.util.JcxfsOutput;

public final class XodusFsUtils {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(XodusFsUtils.class);

    public static void printStats(final XodusFsConfig xodusFsConfig, final JcxfsOutput writer)
            throws FileOpException, JcxfsException {

        {
            final EnvironmentWrapper environmentWrapper = EnvironmentWrapper.forConfig(xodusFsConfig);

            writer.writeLine("db stats:");
            for (final XodusStore dataStore : XodusStore.values()) {
                final Store store = environmentWrapper.getStore(dataStore);
                final long count = environmentWrapper.doCompute(store::count);
                writer.writeLine(dataStore + " records: " + count);
            }
            writer.writeLine("\n\nPath Store Output:");
            environmentWrapper.close();
        }

        try (final XodusFs xodusFs = XodusFs.open(xodusFsConfig)) {

            for (final StoreBucket storeBucket : xodusFs.storeBuckets()) {
                storeBucket.printStats(writer);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void initXodusFileStore(
            final XodusFsConfig xodusFsConfig, final EnvParams initParameters, final XodusFsParams xodusFsParams)
            throws JcxfsException {
        try {
            initImpl(xodusFsConfig, initParameters, xodusFsParams);
        } catch (final Exception e) {
            throw new JcxfsException("init: io error during init: " + e.getMessage(), e);
        }
    }

    private static void initImpl(
            final XodusFsConfig xodusFsConfig, final EnvParams initParameters, XodusFsParams xodusFsParams)
            throws JcxfsException, IOException {
        if (!Files.exists(xodusFsConfig.path())) {
            throw new JcxfsException("init: path does not exist");
        }

        if (!Files.isDirectory(xodusFsConfig.path())) {
            throw new JcxfsException("init: path is not a directory");
        }

        try (final Stream<Path> entries = Files.list(xodusFsConfig.path())) {
            if (entries.findAny().isPresent()) {
                throw new JcxfsException("init: path is not empty");
            }
        }

        final String cipher = CipherGenerator.makeCipher(initParameters.passwordClass(), xodusFsConfig.password());

        initParameters.writeToFile(xodusFsConfig.path());

        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setCipherId(initParameters.cipherClass().getName());
        environmentConfig.setCipherKey(cipher);
        environmentConfig.setCipherBasicIV(initParameters.iv());

        final Environment env = Environments.newInstance(xodusFsConfig.path().toFile(), environmentConfig);
        env.close();
        final EnvironmentWrapper environmentWrapper = EnvironmentWrapper.forConfig(xodusFsConfig, initParameters);
        environmentWrapper.writeXodusFsParams(xodusFsParams);
        environmentWrapper.close();
        LOGGER.info(() ->
                "created xodus env at " + xodusFsConfig.path() + " with " + initParameters + " and " + xodusFsParams);
    }

    static Environment openEnv(final XodusFsConfig config) throws JcxfsException {

        try {
            final EnvParams envParams = EnvParams.readFromFile(config.path());
            final String cipher = CipherGenerator.makeCipher(envParams.passwordClass(), config.password());

            final EnvironmentConfig environmentConfig = new EnvironmentConfig();
            // environmentConfig.setGcUtilizationFromScratch(true);
            // environmentConfig.setGcMinUtilization(90);

            environmentConfig.setCipherId(envParams.cipherClass().getName());
            environmentConfig.setCipherKey(cipher);
            environmentConfig.setCipherBasicIV(envParams.iv());

            final Environment env = Environments.newInstance(config.path().toFile(), environmentConfig);
            LOGGER.info("opened xodus env at " + config.path());
            return env;
        } catch (final Exception e) {
            final String msg = "error opening xodus database: " + e.getMessage();
            LOGGER.debug(() -> msg, e);
            throw new JcxfsException(msg, e);
        }
    }
}

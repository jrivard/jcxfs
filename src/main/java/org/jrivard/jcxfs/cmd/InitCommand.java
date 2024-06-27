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

package org.jrivard.jcxfs.cmd;

import java.util.random.RandomGenerator;
import jetbrains.exodus.crypto.StreamCipherProvider;
import org.jrivard.jcxfs.ArgonGenerator;
import org.jrivard.jcxfs.xodusfs.EnvParams;
import org.jrivard.jcxfs.xodusfs.XodusFs;
import org.jrivard.jcxfs.xodusfs.XodusFsConfig;
import org.jrivard.jcxfs.xodusfs.XodusFsParams;
import org.jrivard.jcxfs.xodusfs.XodusFsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "initialize new repository")
public class InitCommand extends AbstractCommandRunnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InitCommand.class);

    @CommandLine.ParentCommand
    private JcxfsCommandLine parentCommand;

    @CommandLine.Option(
            names = "--xodus-cipher-class",
            description = "xodus streamcipher implementation provider class",
            defaultValue = "jetbrains.exodus.crypto.streamciphers.ChaChaStreamCipherProvider")
    private String cipherClass;

    @CommandLine.Option(
            names = {"--xodus-page-size"},
            paramLabel = "xodus-page-size",
            defaultValue = "4096",
            description = "xodus data page size")
    private int xodusPageSize;

    public int execute(final CommandContext commandContext) throws Exception {

        parentCommand.init();
        LOGGER.trace("beginning init command");

        final XodusFsConfig xodusFsConfig = parentCommand.toXodusConfig();

        final Class<? extends StreamCipherProvider> cipherClazz =
                (Class<? extends StreamCipherProvider>) Class.forName(cipherClass);

        final long iv = RandomGenerator.of("SecureRandom").nextLong();
        final EnvParams envParams = new EnvParams(iv, cipherClazz, ArgonGenerator.class);
        final XodusFsParams xodusFsParams = new XodusFsParams(XodusFs.VERSION, xodusPageSize);

        XodusFsUtils.initXodusFileStore(xodusFsConfig, envParams, xodusFsParams);

        return 0;
    }
}

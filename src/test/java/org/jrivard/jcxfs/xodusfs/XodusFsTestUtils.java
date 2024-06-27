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
import java.security.SecureRandom;
import jetbrains.exodus.crypto.streamciphers.ChaChaStreamCipherProvider;
import org.jrivard.jcxfs.ArgonGenerator;
import org.jrivard.jcxfs.JcxfsException;

public final class XodusFsTestUtils {
    private static final String PASSWORD = "password";
    private static final EnvParams ENV_PARAMS =
            new EnvParams(1, ChaChaStreamCipherProvider.class, ArgonGenerator.class);

    private static final XodusFsParams XODUS_PARAMS = new XodusFsParams(XodusFs.VERSION, 32 * 1024);

    static EnvironmentWrapper makeEnv(final Path junitTemporaryFolder) throws IOException, JcxfsException {
        final Path testPath = junitTemporaryFolder.resolve("jcxfs-junit-temp-test");
        Files.createDirectories(testPath);
        final XodusFsConfig xodusFsConfig = new XodusFsConfig(testPath, PASSWORD);
        XodusFsUtils.initXodusFileStore(xodusFsConfig, ENV_PARAMS, XODUS_PARAMS);
        return EnvironmentWrapper.forConfig(xodusFsConfig);
    }

    static byte[] makeData(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] returnData = new byte[length];
        secureRandom.nextBytes(returnData);
        return returnData;
    }
}

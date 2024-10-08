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
import java.util.UUID;

public final class XodusFsTestUtils {
    private static final String PASSWORD = "password";
    private static final StoredExternalEnvParams ENV_PARAMS = new StoredExternalEnvParams(1, null, null, null);

    private static final StoredInternalEnvParams XODUS_PARAMS = new StoredInternalEnvParams(XodusFs.VERSION, 32 * 1024);

    static EnvironmentWrapper makeEnv(final Path junitTemporaryFolder) throws IOException, JcxfsException {
        final Path testPath = junitTemporaryFolder
                .resolve("jcxfs-junit-temp-test")
                .resolve(UUID.randomUUID().toString());

        Files.createDirectories(testPath);

        final XodusFsUtils.InitParameters initParameters =
                new XodusFsUtils.InitParameters(testPath, PASSWORD, null, null, -1);

        XodusFsUtils.initXodusFileStore(initParameters);

        final RuntimeParameters xodusFsConfig = RuntimeParameters.basic(testPath, PASSWORD);
        return EnvironmentWrapper.forConfig(xodusFsConfig);
    }

    static byte[] makeData(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] returnData = new byte[length];
        //  Arrays.fill( returnData, (byte) 0x24 );
        secureRandom.nextBytes(returnData);
        return returnData;
    }
}

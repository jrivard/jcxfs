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

package org.jrivard.jcxfs.xodusfs.cipher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class ArgonGeneratorTest {

    @Test
    public void loadAndStoreTest() throws Exception {
        final String storedData;
        final String initiaCipher;
        {
            final ArgonAuthMachine argonGenerator = new ArgonAuthMachine();
            argonGenerator.initNewEnv("password");
            storedData = argonGenerator.storeEnv();
            initiaCipher = argonGenerator.readCipher("password");
        }

        final String storedCipher;
        {
            final ArgonAuthMachine argonGenerator = new ArgonAuthMachine();
            argonGenerator.loadEnv(storedData);
            storedCipher = argonGenerator.readCipher("password");
        }
        Assertions.assertEquals(initiaCipher, storedCipher);
    }

    @Test
    public void wrongPasswordTest() throws AuthException {
        final ArgonAuthMachine argonGenerator = new ArgonAuthMachine();
        argonGenerator.initNewEnv("password");
        argonGenerator.readCipher("password");
        Assertions.assertThrows(AuthException.class, () -> argonGenerator.readCipher("wrongpassword"));
    }

    @Test
    public void changePasswordTest() throws Exception {
        final String storedData;
        final String initiaCipher;
        {
            final ArgonAuthMachine argonGenerator = new ArgonAuthMachine();
            argonGenerator.initNewEnv("password");
            storedData = argonGenerator.storeEnv();
            initiaCipher = argonGenerator.readCipher("password");
        }

        final String storedCipher;
        {
            final ArgonAuthMachine argonGenerator = new ArgonAuthMachine();
            argonGenerator.loadEnv(storedData);
            storedCipher = argonGenerator.readCipher("password");
        }
        Assertions.assertEquals(initiaCipher, storedCipher);
    }
}

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

import java.lang.reflect.InvocationTargetException;

public interface AuthMachine {

    void initNewEnv(String password) throws AuthException;

    String readCipher(String password) throws AuthException;

    void loadEnv(String state);

    String storeEnv();

    void changePassword(String originalPassword, String newPassword) throws AuthException;

    static AuthMachine makeInstance(final String className) throws AuthException {
        try {
            final Class<AuthMachine> cipherClass = (Class<AuthMachine>) Class.forName(className);
            return cipherClass.getDeclaredConstructor().newInstance();
        } catch (final InvocationTargetException
                | InstantiationException
                | IllegalAccessException
                | ClassNotFoundException
                | NoSuchMethodException e) {
            throw new AuthException("error generating cipher from password " + e.getMessage(), e);
        }
    }
}

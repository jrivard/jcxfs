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

import java.lang.reflect.InvocationTargetException;
import org.jrivard.jcxfs.xodusfs.util.JcxfsException;

public interface CipherGenerator {
    String makeCipher(String password);

    static String makeCipher(final Class<? extends CipherGenerator> cipherClass, final String password)
            throws JcxfsException {
        try {
            final CipherGenerator cipherGenerator =
                    cipherClass.getDeclaredConstructor().newInstance();
            return cipherGenerator.makeCipher(password);
        } catch (final InvocationTargetException
                | InstantiationException
                | IllegalAccessException
                | NoSuchMethodException e) {
            throw new JcxfsException("error generating cipher from password " + e.getMessage(), e);
        }
    }
}
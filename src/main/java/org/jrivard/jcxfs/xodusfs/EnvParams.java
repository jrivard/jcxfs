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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import jetbrains.exodus.crypto.StreamCipherProvider;
import org.jrivard.jcxfs.CipherGenerator;
import org.jrivard.jcxfs.JcxfsException;
import org.jrivard.jcxfs.JcxfsLogger;

public record EnvParams(
        long iv, Class<? extends StreamCipherProvider> cipherClass, Class<? extends CipherGenerator> passwordClass) {

    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(EnvParams.class);

    private static final String FILE_NAME = "jcxfs.env";

    private static final String KEY_IV = "iv";
    private static final String KEY_CIPHER_CLASS = "cipher_class";
    private static final String KEY_PASSWORD_CLASS = "password_class";

    private static final String COMMENT =
            "Parameters for jcxfs database.  The database can not be opened if this file is modified or removed.";

    public EnvParams {
        if (iv == 0) {
            throw new IllegalStateException("non-zero iv value required");
        }

        Objects.requireNonNull(cipherClass);
    }

    public void writeToFile(final Path directoryPath) throws IOException {
        final Properties props = new Properties();

        props.setProperty(KEY_IV, HexFormat.of().toHexDigits(iv));
        props.setProperty(KEY_CIPHER_CLASS, cipherClass.getName());
        props.setProperty(KEY_PASSWORD_CLASS, passwordClass.getName());

        try (final OutputStream os = Files.newOutputStream(directoryPath.resolve(FILE_NAME))) {
            props.store(os, COMMENT);
        }
    }

    public static EnvParams readFromFile(final Path directoryPath) throws JcxfsException {

        final Path filePath = directoryPath.resolve(FILE_NAME);
        if (!Files.exists(filePath)) {
            throw new JcxfsException(FILE_NAME + " not found, unable to open database");
        }
        try (final InputStream os = Files.newInputStream(filePath)) {
            final Properties props = new Properties();
            props.load(os);
            return new EnvParams(
                    HexFormat.fromHexDigitsToLong(props.getProperty(KEY_IV)),
                    (Class<? extends StreamCipherProvider>) Class.forName(props.getProperty(KEY_CIPHER_CLASS)),
                    (Class<? extends CipherGenerator>) Class.forName(props.getProperty(KEY_PASSWORD_CLASS)));

        } catch (final Exception e) {
            throw new JcxfsException("init: error reading " + FILE_NAME + ", error: " + e.getMessage(), e);
        }
    }
}

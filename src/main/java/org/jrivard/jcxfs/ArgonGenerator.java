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

package org.jrivard.jcxfs;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public class ArgonGenerator implements CipherGenerator {
    @Override
    public String makeCipher(final String password) {
        Objects.requireNonNull(password);
        if (password.isEmpty()) {
            throw new IllegalArgumentException("non-empty password required");
        }
        final Argon2BytesGenerator argon2PasswordEncoder = new Argon2BytesGenerator();
        argon2PasswordEncoder.init(new Argon2Parameters.Builder().build());
        final byte[] output = new byte[32];
        argon2PasswordEncoder.generateBytes(password.getBytes(StandardCharsets.UTF_8), output);
        final StringBuilder sb = new StringBuilder();
        for (final byte b : output) {
            sb.append(HexFormat.of().toHexDigits(b));
        }
        return sb.toString();
    }
}

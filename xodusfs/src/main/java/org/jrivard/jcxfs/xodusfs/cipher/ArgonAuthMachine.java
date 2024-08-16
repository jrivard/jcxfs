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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.jrivard.jcxfs.xodusfs.util.JsonUtil;
import org.jrivard.jcxfs.xodusfs.util.XodusFsLogger;

public class ArgonAuthMachine implements AuthMachine {
    private static final XodusFsLogger LOGGER = XodusFsLogger.getLogger(ArgonAuthMachine.class);

    private static final Charset CHAR_SET = StandardCharsets.UTF_8;
    private static final int SALT_LENGTH = 64;
    private static final int DEK_LENGTH = 32;

    public static final String CIPHER_SPEC = "AES/CBC/PKCS5Padding"; // "AES/GCM/NoPadding";
    public static final String KEY_SPEC = "AES";

    private static final String VERSION = "1";

    private record InternalState(String version, String salt, String dek) {
        private InternalState {
            Objects.requireNonNull(salt);
            if (salt.isEmpty()) {
                throw new IllegalArgumentException("non-empty password required");
            }
            Objects.requireNonNull(dek);
            if (dek.isEmpty()) {
                throw new IllegalArgumentException("non-empty dek required");
            }
        }
    }

    private InternalState internalState;

    @Override
    public String readCipher(final String password) throws AuthException {
        checkEnv();
        final String kek = decryptKEK(password, internalState.salt());
        return decryptDEK(kek, internalState.dek());
    }

    @Override
    public void loadEnv(final String state) {
        this.internalState = JsonUtil.deserialize(state, InternalState.class);
    }

    @Override
    public String storeEnv() {
        checkEnv();
        return JsonUtil.serialize(internalState);
    }

    @Override
    public void changePassword(final String originalPassword, final String newPassword) throws AuthException {
        checkEnv();
        final String originalKek = decryptKEK(originalPassword, internalState.salt);
        final String newSalt = makeKey(SALT_LENGTH);
        final String newKek = decryptKEK(newPassword, newSalt);
        final String dek = decryptDEK(originalKek, internalState.dek());
        final String newEncryptedDek = encryptDEK(dek, newKek);
        this.internalState = new InternalState(VERSION, newSalt, newEncryptedDek);
    }

    @Override
    public void initNewEnv(final String password) throws AuthException {
        final String salt = makeKey(SALT_LENGTH);
        final String raw_dek = makeKey(DEK_LENGTH);

        final String kek = decryptKEK(password, salt);
        final String encrypted_dek = encryptDEK(raw_dek, kek);

        LOGGER.debug(() -> "initialized and validated authentication data");
        this.internalState = new InternalState(VERSION, salt, encrypted_dek);
    }

    private void checkEnv() {
        if (internalState == null) {
            throw new IllegalStateException("not yet initialized");
        }
    }

    private static String decryptDEK(final String kek, final String encryptedDek) throws AuthException {

        try {
            final SecretKey key;
            {
                final byte[] kek_ba = HexFormat.of().parseHex(kek);
                key = new SecretKeySpec(kek_ba, KEY_SPEC);
            }

            final Cipher cipher;
            final byte[] cipherText;
            {
                final byte[] dek_ba = HexFormat.of().parseHex(encryptedDek);
                // Assuming AES-128

                final byte[] iv = Arrays.copyOfRange(dek_ba, 0, 16);
                cipherText = Arrays.copyOfRange(dek_ba, 16, dek_ba.length);
                cipher = Cipher.getInstance(CIPHER_SPEC);
                cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            }

            final byte[] decodedDek = cipher.doFinal(cipherText);
            return HexFormat.of().formatHex(decodedDek);
        } catch (final InvalidAlgorithmParameterException
                | NoSuchPaddingException
                | IllegalBlockSizeException
                | NoSuchAlgorithmException
                | BadPaddingException
                | InvalidKeyException e) {
            final String msg = "error during dek decryption (password incorrect?): " + e.getMessage();
            LOGGER.error(() -> msg);
            throw new AuthException(msg, e);
        }
    }

    // dek is iv + cipherText
    private static String encryptDEK(final String dek, final String kek) throws AuthException {
        try {
            final SecretKey key;
            {
                final byte[] kek_ba = HexFormat.of().parseHex(kek);
                key = new SecretKeySpec(kek_ba, KEY_SPEC);
            }

            final byte[] dek_ba = HexFormat.of().parseHex(dek);

            final IvParameterSpec ivParams;
            {
                final SecureRandom random = new SecureRandom();
                final byte[] iv = new byte[16];
                random.nextBytes(iv);
                ivParams = new IvParameterSpec(iv);
            }

            final Cipher cipher;
            {
                cipher = Cipher.getInstance(CIPHER_SPEC);
                cipher.init(Cipher.ENCRYPT_MODE, key, ivParams);
            }

            final byte[] encryptedKek = cipher.doFinal(dek_ba);
            final byte[] appendedIvAndCipher = appendArrays(ivParams.getIV(), encryptedKek);

            return HexFormat.of().formatHex(appendedIvAndCipher);
        } catch (final InvalidAlgorithmParameterException
                | NoSuchPaddingException
                | IllegalBlockSizeException
                | NoSuchAlgorithmException
                | BadPaddingException
                | IOException
                | InvalidKeyException e) {
            final String msg = "error during dek encryption: " + e.getMessage();
            LOGGER.error(() -> msg);
            throw new AuthException(msg, e);
        }
    }

    public static String decryptKEK(final String password, final String salt) {
        final Argon2BytesGenerator argon2PasswordEncoder = new Argon2BytesGenerator();
        argon2PasswordEncoder.init(new Argon2Parameters.Builder().build());
        final String appendedSaltAndPassword = salt + password;
        final byte[] output = new byte[16];
        argon2PasswordEncoder.generateBytes(appendedSaltAndPassword.getBytes(CHAR_SET), output);
        return HexFormat.of().formatHex(output);
    }

    private static byte[] appendArrays(final byte[]... arrays) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (final byte[] array : arrays) {
            baos.write(array);
        }
        return baos.toByteArray();
    }

    private static String makeKey(final int length) {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] newKey = new byte[length];
        secureRandom.nextBytes(newKey);
        return HexFormat.of().formatHex(newKey);
    }
}

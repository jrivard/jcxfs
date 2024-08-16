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

import static picocli.CommandLine.Spec.Target.MIXEE;

import jetbrains.exodus.crypto.streamciphers.ChaChaStreamCipherProvider;
import org.jrivard.jcxfs.xodusfs.cipher.ArgonAuthMachine;
import picocli.CommandLine;

public class InitOptions {
    private static final int XODUS_PAGE_SIZE_MIN = 64;
    private static final int XODUS_PAGE_SIZE_MAX = 1024 * 1000;

    public enum CipherOption {
        chacha20,
        ;

        private final String implClass = ChaChaStreamCipherProvider.class.getName();

        public String implClass() {
            return implClass;
        }
    }

    public enum AuthOption {
        argon,
        ;

        private final String implClass = ArgonAuthMachine.class.getName();

        public String implClass() {
            return implClass;
        }
    }

    @CommandLine.Spec(MIXEE)
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = "--cipher",
            description = "xodus streamcipher implementation provider class",
            defaultValue = "chacha20")
    protected CipherOption cipherClass;

    @CommandLine.Option(
            names = "--authHash",
            description = "authentication hash kyey provider class",
            defaultValue = "argon")
    protected AuthOption authHash;

    @CommandLine.Option(
            names = {"--xodus-page-size"},
            paramLabel = "xodus-page-size",
            defaultValue = "65536",
            description = "xodus data page size")
    public void setXodusPageSize(final int intValue) {
        if (intValue < XODUS_PAGE_SIZE_MIN || intValue > XODUS_PAGE_SIZE_MAX) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid value '" + intValue + "' for option '--utilization': "
                            + "value is not within "
                            + XODUS_PAGE_SIZE_MIN + "-" + XODUS_PAGE_SIZE_MAX
                            + " range.");
        }
        xodusPageSize = intValue;
    }

    protected int xodusPageSize;
}

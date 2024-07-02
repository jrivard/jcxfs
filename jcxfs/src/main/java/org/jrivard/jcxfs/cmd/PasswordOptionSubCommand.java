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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.jrivard.jcxfs.xodusfs.util.JcxfsException;
import picocli.CommandLine;

public class PasswordOptionSubCommand {
    @CommandLine.Option(
            names = "-w",
            converter = PasswordString.Converter.class,
            paramLabel = "password",
            description = "bind password")
    PasswordString cliPw;

    @CommandLine.Option(
            names = "-W",
            converter = PasswordString.Converter.class,
            paramLabel = "password",
            interactive = true,
            description = "prompt for bind password")
    PasswordString consolePW;

    @CommandLine.Option(
            names = "--pw-file",
            description = "file to read bind password from",
            paramLabel = "password_filename")
    String pwFile;

    String effectivePassword() throws JcxfsException {
        if (pwFile != null && pwFile.length() > 1) {
            try {
                return Files.readString(new File(pwFile).toPath());
            } catch (final IOException e) {
                throw new JcxfsException("unable to read password option file: " + e.getMessage(), e);
            }
        }

        if (cliPw != null && cliPw.value() != null && !cliPw.value().isEmpty()) {
            return cliPw.value();
        }

        return consolePW.value();
    }
}

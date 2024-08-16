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

import org.jrivard.jcxfs.cmd.JcxfsCommandLine;
import picocli.CommandLine;

public final class JcxfsMain {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(JcxfsMain.class);

    public static void main(final String[] args) {
        try {
            final CommandLine commandLine = new CommandLine(new JcxfsCommandLine());
            commandLine.setCaseInsensitiveEnumValuesAllowed(true);
            commandLine.setSubcommandsCaseInsensitive(true);

            if (args == null || args.length == 0) {
                commandLine.usage(System.out);
            } else {
                final int resultCode = commandLine.execute(args);
                if (resultCode == 0) {
                    LOGGER.debug(() -> "normal exit");
                } else {
                    LOGGER.debug(() -> "exit with error " + resultCode);
                }
                System.exit(resultCode);
            }
        } catch (final Exception e) {
            final String errorMsg = "error: " + e.getMessage();
            System.err.println(errorMsg);
            LOGGER.error(() -> errorMsg, e);
            System.exit(-1);
        }
    }
}

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

import java.io.PrintWriter;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.xodusfs.RuntimeParameters;
import org.jrivard.jcxfs.xodusfs.XodusFsUtils;
import picocli.CommandLine;

@CommandLine.Command(name = "dump", description = "get debug info about a repository")
public class DumpCommand extends AbstractCommandRunnable {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(DumpCommand.class);

    @CommandLine.Mixin
    private XodusDbOptions commonOptions = new XodusDbOptions();

    public int execute(final CommandContext commandContext) throws Exception {
        LOGGER.trace(() -> "beginning dump command");

        final RuntimeParameters xodusFsConfig = commonOptions.toRuntimeParams();

        try (final PrintWriter writer = new PrintWriter(System.out)) {
            XodusFsUtils.printStats(xodusFsConfig, writer);
        }
        return 0;
    }
}

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

import java.nio.file.Path;
import java.util.Scanner;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.JcxfsUtil;
import org.jrivard.jcxfs.xodusfs.RuntimeParameters;
import picocli.CommandLine;

@CommandLine.Command(name = "mount", description = "mount a jcxfs database")
public class MountCommand extends AbstractCommandRunnable {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(MountCommand.class);

    @CommandLine.Mixin
    private XodusDbOptions commonOptions = new XodusDbOptions();

    @CommandLine.Parameters(arity = "1", index = "1", description = "mount point", paramLabel = "mountPoint")
    private String fuseMountPath;

    @CommandLine.Option(
            names = {"--noexit"},
            paramLabel = "noexit",
            defaultValue = "false",
            description = "do not wait for keypress to exit")
    private boolean noexit;

    public int execute(final CommandContext commandContext) throws Exception {
        LOGGER.trace(() -> "beginning mount command");

        final RuntimeParameters runtimeParameters = commonOptions.toRuntimeParams();

        final String readonlyText = runtimeParameters.readonly() ? " (readonly)" : "";
        final String xodusPath = runtimeParameters.path().toString();
        final String mountPath = Path.of(fuseMountPath).toString();

        final JcxfsUtil jcxfsUtil = new JcxfsUtil();

        jcxfsUtil.start(runtimeParameters, Path.of(fuseMountPath));
        commandContext.consoleOutput().writeLine("mounted " + xodusPath + readonlyText + " at " + mountPath);

        if (noexit) {
            commandContext.consoleOutput().writeLine("waiting forever...");
            LOGGER.info(() -> "waiting forever...");
            Thread.currentThread().join();
        } else {
            LOGGER.debug(() -> "waiting for exit from console...");
            waitForExitType(commandContext);
        }

        jcxfsUtil.close();

        return 0;
    }

    static void waitForExitType(final CommandContext commandContext) {
        boolean exit = false;
        while (!exit) {
            commandContext.consoleOutput().writeLine("type \"exit\" and press enter key to exit and unmount");
            final String line = new Scanner(System.in).nextLine();
            if (line.toLowerCase().contains("exit")) {
                exit = true;
                commandContext.consoleOutput().writeLine("console line received with exit request");
            } else {
                commandContext.consoleOutput().writeLine("console line received but \"exit\" not in line");
            }
        }
    }
}

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
import org.jrivard.jcxfs.JcxfsEnv;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.xodusfs.XodusFsConfig;
import picocli.CommandLine;

@CommandLine.Command(name = "mount", description = "mount a jcxfs database")
public class MountCommand extends AbstractCommandRunnable {
    private static final JcxfsLogger LOG = JcxfsLogger.getLogger(MountCommand.class);

    @CommandLine.ParentCommand
    private JcxfsCommandLine parentCommand;

    @CommandLine.Parameters(arity = "1", index = "0", description = "mount point", paramLabel = "mountPoint")
    private String fuseMountPath;

    public int execute(final CommandContext commandContext) throws Exception {
        LOG.trace("beginning mount command");

        final JcxfsEnv jcxfsEnv = new JcxfsEnv();

        final XodusFsConfig xodusFsConfig = parentCommand.toXodusConfig();
        jcxfsEnv.start(xodusFsConfig, Path.of(fuseMountPath));
        LOG.info("waiting for keypress...");
        System.out.println("Press any key to exit and unmount");
        new Scanner(System.in).nextLine();
        LOG.info("Keypress received");
        jcxfsEnv.close();

        return 0;
    }
}

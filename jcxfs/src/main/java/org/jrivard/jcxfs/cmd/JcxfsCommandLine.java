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
import java.nio.file.Path;
import org.jrivard.jcxfs.JcxfsConsoleWriter;
import org.jrivard.jcxfs.LogUtil;
import org.jrivard.jcxfs.VersionUtil;
import org.jrivard.jcxfs.xodusfs.JcxfsException;
import org.jrivard.jcxfs.xodusfs.XodusFsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(
        name = "jcxfs",
        description = "Encrypted FUSE file system, Copyright © 2024 Jason Rivard",
        subcommands = {
            InitCommand.class,
            MountCommand.class,
            StatsCommand.class,
            AutoComplete.GenerateCompletion.class,
            CommandLine.HelpCommand.class,
        },
        mixinStandardHelpOptions = true,
        versionProvider = VersionUtil.ManifestVersionProvider.class,
        descriptionHeading = "Use @filename to specify options in a file (one option per line)\n")
public class JcxfsCommandLine {
    private static final Logger LOGGER = LoggerFactory.getLogger(JcxfsCommandLine.class);

    @CommandLine.ParentCommand
    JcxfsCommandLine parentCommand;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = {"--log-level"},
            defaultValue = "NONE",
            paramLabel = "level",
            description = "set log level")
    private LogUtil.LogLevel logLevel;

    @CommandLine.Option(
            names = {"--log-file"},
            paramLabel = "filename",
            description = "log file to append to")
    private String logFile;

    @CommandLine.Option(names = "-db", paramLabel = "dbPath", description = "path to database", required = true)
    private String dbPath;

    @CommandLine.ArgGroup(multiplicity = "1")
    PasswordOptionSubCommand passwordOptionSubCommand;

    XodusFsConfig toXodusConfig() throws JcxfsException {
        return new XodusFsConfig(Path.of(dbPath), passwordOptionSubCommand.effectivePassword());
    }

    protected void init() {
        LogUtil.initLogging(logLevel, logFile);
        LogUtil.logCli(spec.commandLine());
    }

    public int doExecute(final CommandRunnable commandRunnable) {
        init();

        try {
            LOGGER.trace("jcxfs reporting for duty");

            final CommandContext context = new CommandContext(
                    spec.commandLine().getParseResult(), JcxfsConsoleWriter.forWriter(new PrintWriter(System.out)));

            final int result = commandRunnable.execute(context);
            LOGGER.debug("exiting jcxfs" + (result != 0 ? "(" + result + ")" : ""));
            return result;
        } catch (final JcxfsException exception) {
            System.err.println("error: " + exception.getMessage());
        } catch (final Exception e) {
            final String errorMsg = "error: " + e.getMessage();
            LOGGER.error(errorMsg, e);
            System.err.println(errorMsg);
        }
        if (logLevel == null || logLevel == LogUtil.LogLevel.none) {
            System.err.println("use --log-level option for further error information");
        }
        return -1;
    }
}

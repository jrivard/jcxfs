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

import org.jrivard.jcxfs.xodusfs.RuntimeParameters;
import picocli.CommandLine;

import java.nio.file.Path;

import static picocli.CommandLine.Spec.Target.MIXEE;

@CommandLine.Command(mixinStandardHelpOptions = false) // add --help and --version to all commands that have this mixin
public class XodusDbOptions {
    @CommandLine.Spec(MIXEE)
    CommandLine.Model.CommandSpec mixee;

    @CommandLine.Parameters(paramLabel = "dbPath", description = "path to database", index = "0")
    private String dbPath;

    @CommandLine.Option(
            names = {"-readonly"},
            paramLabel = "readonly",
            defaultValue = "false",
            description = "mount as readonly filesystem")
    private boolean readonly;

    @CommandLine.Option(
            names = {"-utilization"},
            paramLabel = "utilization",
            defaultValue = "80",
            description = "xodus-db utilization level")
    public void setUtilizationNumber(final int intValue) {
        if (intValue < 1 || intValue > 90) {
            throw new CommandLine.ParameterException(
                    mixee.commandLine(),
                    "Invalid value '" + intValue + "' for option '--utilization': "
                            + "value is not within 1-90 range.");
        }
        utilization = intValue;
    }

    private int utilization;

    RuntimeParameters toRuntimeParams() throws org.jrivard.jcxfs.xodusfs.JcxfsException {
        return new RuntimeParameters(
                Path.of(dbPath), passwordOptionSubCommand.effectivePassword(), utilization, readonly);
    }

    @CommandLine.ArgGroup(multiplicity = "0..1", exclusive = true)
    private PasswordOptionSubCommand passwordOptionSubCommand;
}

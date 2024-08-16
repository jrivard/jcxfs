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
import org.jrivard.jcxfs.xodusfs.XodusFsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "test", description = "run test process")
public class TestCommand extends AbstractCommandRunnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestCommand.class);

    @CommandLine.Mixin
    private final InitOptions initOptions = new InitOptions();

    @CommandLine.Mixin()
    XodusDbOptions commonOptions = new XodusDbOptions();

    public int execute(final CommandContext commandContext) throws Exception {

        LOGGER.trace("beginning test command");

        final RuntimeParameters xodusFsConfig = commonOptions.toRuntimeParams();

        final XodusFsUtils.InitParameters initParameters = new XodusFsUtils.InitParameters(
                xodusFsConfig.path(),
                xodusFsConfig.password(),
                initOptions.cipherClass.implClass(),
                initOptions.authHash.implClass(),
                initOptions.xodusPageSize);

        return 0;
    }
}

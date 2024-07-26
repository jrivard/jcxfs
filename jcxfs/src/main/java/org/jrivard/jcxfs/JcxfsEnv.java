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

import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseBuilder;
import org.jrivard.jcxfs.fuse.JcxfsFileSystem;
import org.jrivard.jcxfs.xodusfs.JcxfsException;
import org.jrivard.jcxfs.xodusfs.XodusFs;
import org.jrivard.jcxfs.xodusfs.XodusFsConfig;
import org.jrivard.jcxfs.xodusfs.XodusFsUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class JcxfsEnv implements AutoCloseable {
    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(JcxfsEnv.class);

    private XodusFs xodusFs = null;
    private Fuse fuse = null;

    public void start(final XodusFsConfig config, final Path fuseMountPoint) throws JcxfsException {
        xodusFs = initXodusFs(config);
        fuse = initFuseEnv(xodusFs, fuseMountPoint);
    }

    @Override
    public void close() throws Exception {
        LOGGER.debug("closing xodus fs");
        xodusFs.close();
        try {
            LOGGER.info("closing fuse");
            fuse.close();
        } catch (final Exception e) {
            LOGGER.error("failed to close fuse", e);
        }
        xodusFs = null;
        fuse = null;
    }

    private static XodusFs initXodusFs(final XodusFsConfig config) throws JcxfsException {
        Objects.requireNonNull(config);
        if (!Files.exists(config.path())) {
            throw new JcxfsException("database path does not exit");
        }
        if (!Files.isDirectory(config.path())) {
            throw new JcxfsException("database path is not a directory");
        }
        return XodusFsUtils.open(config);
    }

    private static Fuse initFuseEnv(final XodusFs xodusFs, final Path fuseMountPoint) throws JcxfsException {
        Objects.requireNonNull(fuseMountPoint);

        try {
            FuseBuilder.getSupported();
        } catch (final UnsupportedOperationException e) {
            final String message =
                    """
                    Unable to load fuse support for your OS.  Check that the java command line has
                    native access support enabled with '--enable-native-access=ALL-UNNAMED' option.
                    """;
            throw new JcxfsException(message, e);
        }

        final FuseBuilder builder = Fuse.builder();

        final JcxfsFileSystem fuseOps = new JcxfsFileSystem(builder.errno(), xodusFs);
        try {
            final Fuse fuse = builder.build(fuseOps);
            LOGGER.info(() -> "mounting at " + fuseMountPoint);
            fuse.mount("jcxfs", fuseMountPoint, "-s");
            LOGGER.info(() -> "mounted to " + fuseMountPoint + ", ready for bidness.");
            return fuse;
        } catch (final Exception e) {
            LOGGER.trace(() -> "failed to attach fuse mount: " + e.getMessage(), e);
            throw new JcxfsException(e.getMessage());
        }
    }
}

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

package org.jrivard.jcxfs.fuse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.jetbrains.annotations.Nullable;
import org.jrivard.jcxfs.JcxfsLogger;
import org.jrivard.jcxfs.xodusfs.FileOpException;
import org.jrivard.jcxfs.xodusfs.InodeEntry;
import org.jrivard.jcxfs.xodusfs.StatfsInfo;
import org.jrivard.jcxfs.xodusfs.XodusFs;

public class JcxfsFileSystem implements FuseOperations {

    private static final JcxfsLogger LOGGER = JcxfsLogger.getLogger(JcxfsFileSystem.class);

    private final Errno errno;

    private final XodusFs xodusFs;
    private final boolean readonly;

    private final Set<Operation> SUPPORTED_OPERATIONS;

    public JcxfsFileSystem(final Errno errno, final XodusFs xodusFs, final boolean readonly) {
        this.errno = errno;
        this.readonly = readonly;
        this.xodusFs = xodusFs;
        this.SUPPORTED_OPERATIONS = makeSupportedOperations();
    }

    @Override
    public Errno errno() {
        return errno;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return SUPPORTED_OPERATIONS;
    }

    public Set<Operation> makeSupportedOperations() {
        final Set<Operation> theSet = EnumSet.of(
                Operation.GET_ATTR,
                Operation.INIT,
                Operation.READ_DIR,
                Operation.READLINK,
                Operation.STATFS,
                Operation.READ);

        if (!readonly) {
            theSet.addAll(EnumSet.of(
                    Operation.SYMLINK,
                    Operation.RENAME,
                    Operation.TRUNCATE,
                    Operation.MKDIR,
                    Operation.RMDIR,
                    Operation.WRITE,
                    Operation.CREATE,
                    Operation.CHOWN,
                    Operation.CHMOD,
                    Operation.UNLINK,
                    Operation.UTIMENS));
        }

        return Set.copyOf(theSet);
    }

    @Override
    public int getattr(final String path, final Stat stat, final FileInfo fi) {
        return doOp(
                () -> {
                    final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
                    if (optionalDirectoryEntry.isEmpty()) {
                        return -errno.enoent();
                    }

                    final InodeEntry entryAttrs = optionalDirectoryEntry.get();
                    stat.aTime().set(entryAttrs.aTime());
                    stat.cTime().set(entryAttrs.cTime());
                    stat.mTime().set(entryAttrs.mTime());
                    stat.birthTime().set(entryAttrs.bTime());
                    // stat.setGid();
                    // stat.setUid();

                    stat.setMode(entryAttrs.mode());

                    if (entryAttrs.isDirectory()) {
                        stat.setNLink((short) 2);
                    } else if (entryAttrs.isFile()) {
                        final long length = xodusFs.fileLength(path);
                        stat.setNLink((short) 1);
                        stat.setSize(length);
                    }

                    return 0;
                },
                () -> "getattr() path=" + path);
    }

    @Override
    public void init(final FuseConnInfo conn, final FuseConfig cfg) {
        LOGGER.info(() -> "init() major=" + conn.protoMajor() + " minor=" + conn.protoMinor());
    }

    @Override
    public void destroy() {
        LOGGER.info(() -> "destroy()");
    }

    @Override
    public int open(final String path, final FileInfo fi) {
        LOGGER.debug(() -> "open() path=" + path);
        return 0;
    }

    @Override
    public int read(final String path, final ByteBuffer buf, final long size, final long offset, final FileInfo fi) {
        return doOp(
                () -> xodusFs.read(path, buf, size, offset),
                () -> "read() path=" + path + " buf=" + buf + " size=" + size + " offset=" + offset);
    }

    @Override
    public int truncate(final String path, final long size, @Nullable final FileInfo fi) {
        return doOp(
                () -> {
                    xodusFs.truncate(path, size);
                    return 0;
                },
                () -> "truncate() path=" + path + " size=" + size);
    }

    @Override
    public int create(final String path, final int mode, final FileInfo fi) {
        return doOp(
                () -> {
                    xodusFs.createFileEntry(path, mode);
                    return 0;
                },
                () -> "create() path=" + path + " mode=" + mode);
    }

    @Override
    public int release(final String path, final FileInfo fi) {
        LOGGER.debug(() -> "release() path=" + path);
        return 0;
    }

    @Override
    public int mkdir(final String path, final int mode) {
        return doOp(
                () -> {
                    xodusFs.createDirectoryEntry(path, mode);
                    return 0;
                },
                () -> "mkdir() path=" + path + " mode=" + mode);
    }

    @Override
    public int write(final String path, final ByteBuffer buf, final long count, final long offset, final FileInfo fi) {
        return doOp(
                () -> xodusFs.writeFileData(path, buf, count, offset),
                () -> "write() path=" + path + ", buf=" + buf + ", count=" + count + ", offset=" + offset);
    }

    @Override
    public int rmdir(final String path) {
        return doOp(
                () -> {
                    xodusFs.removeDirectoryEntry(path);
                    return 0;
                },
                () -> "rmdir() path=" + path);
    }

    @Override
    public int unlink(final String path) {
        return doOp(
                () -> {
                    xodusFs.removeFileEntry(path);
                    return 0;
                },
                () -> "unlink() path=" + path);
    }

    @Override
    public int opendir(final String path, final FileInfo fi) {
        LOGGER.debug(() -> "opendir() path=" + path);
        return 0;
    }

    private class PathFiller implements Consumer<String> {
        private final String path;
        private final DirFiller filler;

        public PathFiller(final String path, final DirFiller filler) {
            this.path = path;
            this.filler = filler;
        }

        @Override
        public void accept(final String name) {
            try {
                final String fullPath = path + '/' + name;
                filler.fill(name, stat -> getattr(fullPath, stat, null));
            } catch (final IOException e) {
                LOGGER.error(() -> "error while filling path: " + e.getMessage());
            }
        }
    }

    @Override
    public int readdir(
            final String path, final DirFiller filler, final long offset, final FileInfo fi, final int flags) {

        return doOp(
                () -> {
                    try {
                        filler.fill(".");
                        filler.fill("..");
                    } catch (final IOException e) {
                        return -errno.eio();
                    }

                    final var pathFiller = new PathFiller(path, filler);
                    try (final Stream<String> subPaths = xodusFs.directoryListing(path)) {
                        subPaths.forEach(pathFiller);
                    }
                    return 0;
                },
                () -> "readdir() path=" + path + " offset=" + offset);
    }

    @Override
    public int releasedir(final String path, final FileInfo fi) {
        LOGGER.debug(() -> "releasedir() path=" + path);
        return 0;
    }

    @Override
    public int statfs(final String path, final Statvfs statvfs) {

        return doOp(
                () -> {
                    final StatfsInfo statfsInfo = xodusFs.readStatfsInfo();
                    // final long freePages = statfsInfo.freeSpace() / statfsInfo.pageSize();
                    final long freePages = 1_000_000_000;
                    statvfs.setNameMax(255);
                    statvfs.setBsize(statfsInfo.pageSize());
                    statvfs.setFrsize(statfsInfo.pageSize());

                    statvfs.setBlocks(statfsInfo.pagesUsed());
                    statvfs.setBfree(freePages);
                    statvfs.setBavail(freePages);

                    LOGGER.debug(() -> "xodusFs statfs read: " + statfsInfo);
                    return 0;
                },
                () -> "statfs() path=" + path);
    }

    @Override
    public int chmod(final String path, final int mode, @Nullable final FileInfo fi) {
        return doOp(
                () -> {
                    final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
                    if (optionalDirectoryEntry.isEmpty()) {
                        return -errno.enoent();
                    }

                    final InodeEntry existingInode = optionalDirectoryEntry.get();
                    final InodeEntry newInode = existingInode.withMode(mode);

                    xodusFs.writeAttrs(path, newInode);
                    return 0;
                },
                () -> "chown() path=" + path + " mode=" + mode);
    }

    @Override
    public int chown(final String path, final int uid, final int gid, @Nullable final FileInfo fi) {
        return doOp(
                () -> {
                    final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
                    if (optionalDirectoryEntry.isEmpty()) {
                        return -errno.enoent();
                    }

                    final InodeEntry existingInode = optionalDirectoryEntry.get();

                    final int newUid = uid >= 0 ? uid : existingInode.uid();
                    final int newGid = gid >= 0 ? gid : existingInode.gid();

                    final InodeEntry newInode = existingInode.withUidGid(newUid, newGid);

                    xodusFs.writeAttrs(path, newInode);

                    return 0;
                },
                () -> "chown() path=" + path + " uid=" + uid + " gid=" + gid);
    }

    @Override
    public int utimens(final String path, final TimeSpec atime, final TimeSpec mtime, @Nullable final FileInfo fi) {
        return doOp(
                () -> {
                    final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
                    if (optionalDirectoryEntry.isEmpty()) {
                        return -errno.enoent();
                    }

                    final InodeEntry existingInode = optionalDirectoryEntry.get();
                    final InodeEntry newInode = existingInode.withAtimeMtime(atime.get(), mtime.get());

                    xodusFs.writeAttrs(path, newInode);

                    return 0;
                },
                () -> "utimens() path=" + path);
    }

    @Override
    public int rename(final String oldpath, final String newpath, final int flags) {
        return doOp(
                () -> {
                    xodusFs.rename(oldpath, newpath);
                    return 0;
                },
                () -> "rename() oldpath=" + oldpath + " newpath=" + newpath + " flags=" + flags);
    }

    @Override
    public int symlink(final String linkname, final String target) {
        return doOp(
                () -> {
                    xodusFs.createSymLink(linkname, target);
                    return 0;
                },
                () -> "symlink() linkname=" + linkname + " target=" + target);
    }

    @Override
    public int readlink(final String path, final ByteBuffer buf, final long len) {
        return doOp(
                () -> {
                    final String target = xodusFs.readSymLink(path);
                    if (target != null) {
                        final byte[] targetAsBa = target.getBytes(StandardCharsets.UTF_8);
                        buf.put(targetAsBa);
                        buf.put((byte) 0x00);
                    }
                    return 0;
                },
                () -> "readlink() path=" + path);
    }

    private int doOp(final XodusOperation operation, final Supplier<String> logMsg) {
        try {
            final Instant startTime = Instant.now();
            LOGGER.trace(() -> "begin " + logMsg.get());
            final int result = operation.execute();
            LOGGER.debug(logMsg, Duration.between(Instant.now(), startTime));
            return result;
        } catch (final FileOpException e) {
            LOGGER.error(() -> "error during operation " + logMsg.get() + ": " + e.getMessage());
            return -ErrorMapper.forFileOpError(e.getError()).get().errno(errno);
        } catch (final Throwable t) {
            LOGGER.error(() -> "error during operation " + logMsg.get() + ": " + t.getMessage(), t);
        }
        return errno.eio();
    }

    private interface XodusOperation {
        int execute() throws FileOpException;
    }
}

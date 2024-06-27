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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
import org.jrivard.jcxfs.xodusfs.PathKey;
import org.jrivard.jcxfs.xodusfs.XodusFs;

public class JcxfsFileSystem implements FuseOperations {

    private static final JcxfsLogger LOG = JcxfsLogger.getLogger(JcxfsFileSystem.class);

    private final Errno errno;

    private final XodusFs xodusFs;

    public JcxfsFileSystem(final Errno errno, final XodusFs xodusFs) {
        this.errno = errno;
        this.xodusFs = xodusFs;
    }

    @Override
    public Errno errno() {
        return errno;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return EnumSet.of(
                // Operation.DESTROY,
                // Operation.OPEN,
                // Operation.OPEN_DIR,
                // Operation.RELEASE,
                // Operation.RELEASE_DIR,
                Operation.GET_ATTR,
                Operation.INIT,
                Operation.READ_DIR,
                Operation.SYMLINK,
                Operation.READLINK,
                Operation.RENAME,
                Operation.STATFS,
                Operation.TRUNCATE,
                Operation.MKDIR,
                Operation.RMDIR,
                Operation.READ,
                Operation.WRITE,
                Operation.CREATE,
                Operation.CHOWN,
                Operation.CHMOD,
                Operation.UTIMENS,
                Operation.UNLINK);
    }

    @Override
    public int getattr(final String path, final Stat stat, final FileInfo fi) {
        LOG.debug(() -> "getattr() path=" + path);

        try {
            final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
            if (optionalDirectoryEntry.isEmpty()) {
                return -errno.enoent();
            }

            final InodeEntry entryAttrs = optionalDirectoryEntry.get();
            stat.aTime().set(entryAttrs.aTime());
            stat.cTime().set(entryAttrs.cTime());
            stat.mTime().set(entryAttrs.mTime());
            stat.birthTime().set(entryAttrs.bTime());
            // stat.setGid(entryAttrs.gid());
            // stat.setUid(entryAttrs.uid());

            stat.setMode(entryAttrs.mode());

            if (entryAttrs.isDirectory()) {
                stat.setNLink((short) 2);
            } else if (entryAttrs.isFile()) {
                final long length = xodusFs.fileLength(path);
                stat.setNLink((short) 1);
                stat.setSize(length);
            }

            return 0;
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(final FuseConnInfo conn, final FuseConfig cfg) {
        LOG.info(() -> "init() major=" + conn.protoMajor() + " minor=" + conn.protoMinor());
    }

    @Override
    public void destroy() {
        LOG.info("destroy()");
    }

    @Override
    public int open(final String path, final FileInfo fi) {
        LOG.debug(() -> "open() path=" + path);
        return 0;
    }

    @Override
    public int read(final String path, final ByteBuffer buf, final long size, final long offset, final FileInfo fi) {
        LOG.debug(() -> "read() path=" + path + " buf=" + buf + " size=" + size + "  offset=" + offset);
        try {
            return xodusFs.read(path, buf, size, offset);
        } catch (final FileOpException e) {
            LOG.error("error reading file: " + e.getMessage());
            return -e.getError().errno(errno);
        } catch (final Throwable t) {
            LOG.error("error reading file: " + t, t);
        }
        return errno.eio();
    }

    @Override
    public int truncate(final String path, final long size, @Nullable final FileInfo fi) {
        LOG.debug(() -> "truncate() path=" + path + " size=" + size);
        try {
            xodusFs.truncate(path, size);
        } catch (final FileOpException e) {
            LOG.error("error truncating file: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int create(final String path, final int mode, final FileInfo fi) {
        LOG.debug(() -> "create() path=" + path + " mode=" + mode);
        try {
            xodusFs.createFileEntry(path, mode);
        } catch (final FileOpException e) {
            LOG.error("error creating file: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int release(final String path, final FileInfo fi) {
        LOG.debug(() -> "release() path=" + path);
        return 0;
    }

    @Override
    public int mkdir(final String path, final int mode) {
        LOG.debug(() -> "mkdir() path=" + path + " mode=" + mode);
        try {
            xodusFs.createDirectoryEntry(path, mode);
        } catch (final FileOpException e) {
            LOG.error("error making directory: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int write(final String path, final ByteBuffer buf, final long count, final long offset, final FileInfo fi) {
        LOG.debug(() -> "write() path=" + path + ", buf=" + buf + ", count=" + count + ", offset=" + offset);
        try {
            return xodusFs.writeFileData(path, buf, count, offset);
        } catch (final FileOpException e) {
            LOG.error("error writing file: " + e.getMessage());
            return -e.getError().errno(errno);
        } catch (final Throwable t) {
            return errno.eio();
        }
    }

    @Override
    public int rmdir(final String path) {
        LOG.debug(() -> "rmdir() path=" + path);
        try {
            xodusFs.removeDirectoryEntry(path);
        } catch (final FileOpException e) {
            LOG.error("error removing directory: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int unlink(final String path) {
        LOG.debug("unlink() path=" + path);
        try {
            xodusFs.removeFileEntry(path);
        } catch (final FileOpException e) {
            LOG.error("error unlinking file: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int opendir(final String path, final FileInfo fi) {
        LOG.debug("opendir() path=" + path);
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
                final String fullPath = PathKey.of(path).append(name).path();
                filler.fill(name, stat -> getattr(fullPath, stat, null));
            } catch (final IOException e) {
                LOG.error("error while filling path: " + e.getMessage());
            }
        }
    }

    @Override
    public int readdir(
            final String path, final DirFiller filler, final long offset, final FileInfo fi, final int flags) {
        LOG.debug("readdir() path=" + path + " offset=" + offset);

        try {
            filler.fill(".");
            filler.fill("..");
        } catch (final IOException e) {
            return -errno.eio();
        }

        try {
            final var pathFiller = new PathFiller(path, filler);
            xodusFs.directoryListing(path).forEach(pathFiller);
            return 0;
        } catch (final FileOpException e) {
            LOG.error("error listing dir contents: " + e.getMessage());
            return -e.getError().errno(errno);
        }
    }

    @Override
    public int releasedir(final String path, final FileInfo fi) {
        LOG.debug(() -> "releasedir() path=" + path);
        return 0;
    }

    @Override
    public int statfs(final String path, final Statvfs statvfs) {
        LOG.debug(() -> "statfs() path=" + path);
        try {
            final XodusFs.StatfsInfo statfsInfo = xodusFs.readStatfsInfo();
            // final long freePages = statfsInfo.freeSpace() / statfsInfo.pageSize();
            final long freePages = 1_000_000_000;
            statvfs.setNameMax(255);
            statvfs.setBsize(statfsInfo.pageSize());
            statvfs.setFrsize(statfsInfo.pageSize());

            statvfs.setBlocks(statfsInfo.pagesUsed());
            statvfs.setBfree(freePages);
            statvfs.setBavail(freePages);

            LOG.debug(() -> "xodusFs statfs read: " + statfsInfo);
            return 0;
        } catch (final FileOpException e) {
            LOG.error(() -> "error reading xodus statfs: " + e.getMessage(), e);
        }
        return -1;
    }

    @Override
    public int chmod(final String path, final int mode, @Nullable final FileInfo fi) {
        LOG.debug(() -> "chown() path=" + path + " mode=" + mode);

        try {
            final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
            if (optionalDirectoryEntry.isEmpty()) {
                return -errno.enoent();
            }

            final InodeEntry existingInode = optionalDirectoryEntry.get();
            final InodeEntry newInode = existingInode.withMode(mode);

            xodusFs.writeAttrs(path, newInode);

            return 0;
        } catch (final FileOpException e) {
            LOG.error("error executing chmod: " + e.getMessage());
            return -e.getError().errno(errno);
        }
    }

    @Override
    public int chown(final String path, final int uid, final int gid, @Nullable final FileInfo fi) {
        LOG.debug(() -> "chown() path=" + path + " uid=" + uid + " gid=" + gid);

        try {
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
        } catch (final FileOpException e) {
            LOG.error("error executing chown: " + e.getMessage());
            return -e.getError().errno(errno);
        }
    }

    @Override
    public int setxattr(final String path, final String name, final ByteBuffer value, final int flags) {
        return FuseOperations.super.setxattr(path, name, value, flags);
    }

    @Override
    public int utimens(final String path, final TimeSpec atime, final TimeSpec mtime, @Nullable final FileInfo fi) {
        LOG.debug(() -> "utimens() path=" + path);

        try {
            final Optional<InodeEntry> optionalDirectoryEntry = xodusFs.readAttrs(path);
            if (optionalDirectoryEntry.isEmpty()) {
                return -errno.enoent();
            }

            final InodeEntry existingInode = optionalDirectoryEntry.get();
            final InodeEntry newInode = existingInode.withAtimeMtime(atime.get(), mtime.get());

            xodusFs.writeAttrs(path, newInode);

            return 0;
        } catch (final FileOpException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int rename(final String oldpath, final String newpath, final int flags) {
        LOG.debug(() -> "rename() oldpath=" + oldpath + " newpath=" + newpath + " flags=" + flags);
        try {
            xodusFs.rename(oldpath, newpath);
        } catch (final FileOpException e) {
            LOG.error("error executing rename: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int symlink(final String linkname, final String target) {
        LOG.debug(() -> "symlink() linkname=" + linkname + " target=" + target);
        try {
            xodusFs.createSymLink(linkname, target);
        } catch (final FileOpException e) {
            LOG.error("error creating symlink: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }

    @Override
    public int readlink(String path, ByteBuffer buf, long len) {
        LOG.debug(() -> "readlink() path=" + path);
        try {
            final String target = xodusFs.readSymLink(path);
            if (target != null) {
                final byte[] targetAsBa = target.getBytes(StandardCharsets.UTF_8);
                buf.put(targetAsBa);
                buf.put((byte) 0x00);
            }
        } catch (final FileOpException e) {
            LOG.error("error executing rename: " + e.getMessage());
            return -e.getError().errno(errno);
        }
        return 0;
    }
}

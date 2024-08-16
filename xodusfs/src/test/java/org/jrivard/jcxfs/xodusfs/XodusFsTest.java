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

package org.jrivard.jcxfs.xodusfs;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class XodusFsTest {
    @Test
    void createWriteLength(@TempDir Path tempFolder) throws Exception {
        final XodusFs xodusFs = XodusFsUtils.open(XodusFsTestUtils.makeEnv(tempFolder));

        final String fileName = "/file1";
        final int length = 5555;
        xodusFs.createFileEntry(fileName, InodeEntry.newFileEntry().mode());
        final byte[] data = XodusFsTestUtils.makeData(length);
        {
            final ByteBuffer fileContents = ByteBuffer.wrap(data);
            xodusFs.writeFileData(fileName, fileContents, length, 0);
        }

        Assertions.assertEquals(length, xodusFs.fileLength(fileName));
    }

    @Test
    void simpleCreateWriteReadFile(@TempDir Path tempFolder) throws Exception {
        final XodusFs xodusFs = XodusFsUtils.open(XodusFsTestUtils.makeEnv(tempFolder));

        final String fileName = "/file1";
        final int length = 5555;
        xodusFs.createFileEntry(fileName, InodeEntry.newFileEntry().mode());
        final byte[] data = XodusFsTestUtils.makeData(length);
        {
            final ByteBuffer fileContents = ByteBuffer.wrap(data);
            xodusFs.writeFileData(fileName, fileContents, length, 0);
        }

        {
            final ByteBuffer fileContents = ByteBuffer.allocate(length);
            xodusFs.read(fileName, fileContents, length, 0);
            Assertions.assertArrayEquals(data, fileContents.array());
        }
    }

    @Test
    void simpleCreateWriteDelete(@TempDir Path tempFolder) throws Exception {
        final XodusFs xodusFs = XodusFsUtils.open(XodusFsTestUtils.makeEnv(tempFolder));

        final String fileName = "/file1";
        final int length = 5555;
        final byte[] data = XodusFsTestUtils.makeData(length);

        xodusFs.createFileEntry(fileName, InodeEntry.newFileEntry().mode());

        {
            final ByteBuffer fileContents = ByteBuffer.wrap(data);
            xodusFs.writeFileData(fileName, fileContents, length, 0);
        }

        {
            final ByteBuffer fileContents = ByteBuffer.allocate(length);
            xodusFs.read(fileName, fileContents, length, 0);
            Assertions.assertArrayEquals(data, fileContents.array());
        }

        xodusFs.removeFileEntry(fileName);

        Assertions.assertThrows(FileOpException.class, () -> {
            xodusFs.read(fileName, ByteBuffer.allocate(length), length, 0);
        });
    }

    @Test
    void readSubPaths(@TempDir Path tempFolder) throws Exception {
        final XodusFs xodusFs = XodusFsUtils.open(XodusFsTestUtils.makeEnv(tempFolder));

        final List<PathKey> subPaths = List.of(
                PathKey.of("/1"),
                PathKey.of("/2"),
                PathKey.of("/3"),
                PathKey.of("/1/a"),
                PathKey.of("/1/b"),
                PathKey.of("/1/c"),
                PathKey.of("/1/a/aaa"),
                PathKey.of("/1/a/bbb"),
                PathKey.of("/1/a/ccc"));
        for (final PathKey pathKey : subPaths) {
            xodusFs.createDirectoryEntry(
                    pathKey.path(), InodeEntry.newDirectoryEntry().mode());
        }

        try (final Stream<String> dirListingStream = xodusFs.directoryListing("/")) {
            final List<String> expectedSubPaths = List.of("1", "2", "3");
            Assertions.assertEquals(expectedSubPaths, dirListingStream.toList());
        }

        try (final Stream<String> dirListingStream = xodusFs.directoryListing("/1/a")) {
            final List<String> expectedSubPaths = List.of("aaa", "bbb", "ccc");
            Assertions.assertEquals(expectedSubPaths, dirListingStream.toList());
        }
    }
}

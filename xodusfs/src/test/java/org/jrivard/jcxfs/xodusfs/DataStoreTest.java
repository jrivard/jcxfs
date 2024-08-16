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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
public class DataStoreTest {

    @ParameterizedTest
    @MethodSource("testReadWriteArguments")
    void testReadWrite(final DataStore.DataStoreImplType dataStoreImplType, final int size, @TempDir Path tempFolder)
            throws Exception {
        final EnvironmentWrapper environmentWrapper = XodusFsTestUtils.makeEnv(tempFolder);
        final DataStore dataStore = dataStoreImplType.makeImpl(environmentWrapper);
        final long fid = 200;
        final byte[] testData = org.jrivard.jcxfs.xodusfs.XodusFsTestUtils.makeData(size);
        {
            final ByteBuffer buffer = ByteBuffer.wrap(testData);
            environmentWrapper.doExecute(txn -> dataStore.writeData(txn, fid, buffer, size, 0));
        }
        {
            final ByteBuffer buffer = ByteBuffer.allocate(size);
            environmentWrapper.doCompute(txn -> dataStore.readData(txn, fid, buffer, size, 0));
            final byte[] ba = buffer.array();
            Assertions.assertArrayEquals(testData, ba);
        }
    }

    static Stream<Arguments> testReadWriteArguments() {
        final List<Arguments> arguments = new ArrayList<>();
        for (final DataStore.DataStoreImplType type : List.of(DataStore.DataStoreImplType.byte_array)) {
            for (final int size : new int[] {23, 1024, 1025, 4096, 10 * 1024, 128 * 1024, 256 * 1024}) {
                arguments.add(Arguments.of(type, size));
            }
            arguments.add(Arguments.of(type, new SecureRandom().nextInt(1, 128 * 1024)));
        }
        return arguments.stream();
    }
}

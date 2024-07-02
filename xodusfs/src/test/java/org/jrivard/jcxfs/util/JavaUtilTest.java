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

package org.jrivard.jcxfs.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jrivard.jcxfs.xodusfs.util.JavaUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavaUtilTest {
    @ParameterizedTest
    @MethodSource("suffixNullCountArguments")
    void suffixNullCountArray(final byte[] byteArray, final int expectedSuffix) throws Exception {
        Assertions.assertEquals(expectedSuffix, JavaUtil.suffixNullCount(byteArray));
    }

    @ParameterizedTest
    @MethodSource("suffixNullCountArguments")
    void suffixNullCountBuffer(final byte[] byteArray, final int expectedSuffix) throws Exception {
        Assertions.assertEquals(expectedSuffix, JavaUtil.suffixNullCount(ByteBuffer.wrap(byteArray)));
    }

    @Test
    void splitBufferTest() {
        final byte[] firstArray = new byte[] {0x10, 0x10, 0x00, 0x00};
        final byte[] secondArray = new byte[] {0x10, 0x10};
        final ByteBuffer firstBuffer = ByteBuffer.wrap(firstArray);
        final ByteBuffer secondByteBuffer =
                firstBuffer.slice(0, firstBuffer.limit() - JavaUtil.suffixNullCount(firstBuffer));
        final byte[] resultArray = secondByteBuffer.array();
        // Assertions.assertArrayEquals( secondArray, resultArray );
    }

    static Stream<Arguments> suffixNullCountArguments() {
        final List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(new byte[] {0x10, 0x10, 0x10, 0x10}, 0));
        arguments.add(Arguments.of(new byte[] {0x10, 0x10, 0x10, 0x00}, 1));
        arguments.add(Arguments.of(new byte[] {0x10, 0x10, 0x00, 0x00}, 2));
        arguments.add(Arguments.of(new byte[] {0x00, 0x10, 0x00, 0x00}, 2));
        arguments.add(Arguments.of(new byte[] {0x10, 0x00, 0x00, 0x00}, 3));
        return arguments.stream();
    }
}

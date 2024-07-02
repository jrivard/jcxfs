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

package org.jrivard.jcxfs.xodusfs.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class JavaUtil {
    public static String padRight(final String input, final int length, final char appendChar) {
        return padImpl(input, length, appendChar, true);
    }

    public static String padLeft(final String input, final int length, final char appendChar) {
        return padImpl(input, length, appendChar, false);
    }

    private static String padImpl(final String input, final int length, final char appendChar, final boolean right) {
        if (input == null) {
            return "";
        }

        if (input.length() >= length) {
            return input;
        }

        final char[] charArray = new char[length - input.length()];
        Arrays.fill(charArray, appendChar);
        final String paddingString = new String(charArray);

        return right ? input + paddingString : paddingString + input;
    }

    public static int suffixNullCount(final byte[] data) {
        if (data == null) {
            return 0;
        }

        int suffixNulls = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] == 0x00) {
                suffixNulls++;
            } else {
                break;
            }
        }
        return suffixNulls;
    }

    public static int suffixNullCount(final ByteBuffer data) {
        if (data == null) {
            return 0;
        }

        final int limit = data.limit();
        int suffixNulls = 0;
        for (int i = limit - 1; i >= 0; i--) {
            if (data.get(i) == 0x00) {
                suffixNulls++;
            } else {
                break;
            }
        }
        return suffixNulls;
    }
}

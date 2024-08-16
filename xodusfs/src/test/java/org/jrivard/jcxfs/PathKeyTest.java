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

import java.util.List;
import org.jrivard.jcxfs.xodusfs.PathKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PathKeyTest {

    @Test
    public void parentPath() {
        Assertions.assertEquals("/", (PathKey.of("/jason").parent().path()));
        Assertions.assertEquals("/parent", PathKey.of("/parent/jason").parent().path());
    }

    @Test
    public void nameFromPath() {
        Assertions.assertEquals("jason", PathKey.of("/jason").suffix());
        Assertions.assertEquals("jason", PathKey.of("/parent/jason").suffix());
    }

    @Test
    public void segments() {
        final String path = "/amb/.java/.userPrefs/jetbrains/_";
        final PathKey pathKey = PathKey.of(path);
        final List<String> segments = pathKey.segments();
        Assertions.assertEquals("amb", segments.get(0));
        Assertions.assertEquals(".java", segments.get(1));
        Assertions.assertEquals(".userPrefs", segments.get(2));
        Assertions.assertEquals("jetbrains", segments.get(3));
        Assertions.assertEquals("_", segments.get(4));
    }

    @Test
    public void verifyPathSyntax() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/../bad"));

        Assertions.assertDoesNotThrow(() -> PathKey.of("/"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("//"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("///"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("//good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("///good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/.good./good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/..good../good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/good/.good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/good/..good"));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/good/good."));
        Assertions.assertDoesNotThrow(() -> PathKey.of("/good/good/good.."));

        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of(""));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("bad"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad//"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad//bad"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/../bad"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/.../bad"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/.."));
        Assertions.assertThrows(IllegalArgumentException.class, () -> PathKey.of("/bad/..."));
    }
}

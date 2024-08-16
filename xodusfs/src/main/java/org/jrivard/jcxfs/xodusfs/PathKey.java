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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;

public record PathKey(String path) implements StoreKey {
    private static final String PATH_SEPARATOR = "/";
    private static final PathKey ROOT_PATH = new PathKey("/");

    public PathKey {
        Objects.requireNonNull(path);
        String effectivePath = path;

        while (effectivePath.startsWith("//")) {
            effectivePath = effectivePath.substring(1);
        }

        verifyPathSyntax(effectivePath);

        path = effectivePath;
    }

    public static PathKey of(final String path) {
        return new PathKey(path);
    }

    public String path() {
        return path;
    }

    public ByteIterable toByteIterable() {
        return StringBinding.stringToEntry(this.path);
    }

    public PathKey parent() {
        if (isRoot()) {
            throw new IllegalArgumentException("no parent of root path");
        }
        final String parentPath = path().substring(0, path().lastIndexOf(PATH_SEPARATOR));
        if (parentPath.isEmpty()) {
            return ROOT_PATH;
        }
        return new PathKey(parentPath);
    }

    public List<String> segments() {
        return segments(path);
    }

    private static List<String> segments(final String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(fullPath.split("\\/"))
                .filter(Objects::nonNull)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    public String suffix() {
        return path.substring(path.lastIndexOf(PATH_SEPARATOR) + 1);
    }

    private static void verifyPathSyntax(final String path) {

        if (path.equals(PATH_SEPARATOR)) {
            return;
        }

        if (!path.contains(PATH_SEPARATOR)) {
            throw new IllegalArgumentException("path must begin with separator");
        }

        if (path.endsWith(PATH_SEPARATOR)) {
            throw new IllegalArgumentException("path may not end with separator");
        }

        if (path.contains("//")) {
            throw new IllegalArgumentException("path may not contain empty segment");
        }

        for (final String segment : segments(path)) {
            if (stringIsAllOfCharacter(segment, '.')) {
                throw new IllegalArgumentException("path may not contain parent reference");
            }
        }
    }

    public static boolean isRoot(final String path) {
        return ROOT_PATH.path().equals(path);
    }

    public boolean isRoot() {
        return isRoot(path);
    }

    private static boolean stringIsAllOfCharacter(final String input, final char testChar) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) != testChar) {
                return false;
            }
        }

        return true;
    }
}

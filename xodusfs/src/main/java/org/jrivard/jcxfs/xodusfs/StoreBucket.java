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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Map;
import jetbrains.exodus.env.Transaction;

interface StoreBucket {
    int CACHE_MAX_ITEMS = 1000;

    void printDumpOutput(XodusConsoleWriter writer);

    Map<String, String> runtimeStats();

    long size(final Transaction txn);

    void close();

    static <K, V> Cache<K, V> makeCache(final EnvironmentWrapper environmentWrapper) {
        return Caffeine.newBuilder().maximumSize(CACHE_MAX_ITEMS).build();
    }
}

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

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class StatCounterBundle<K extends Enum<K>> {
    private final Class<K> keyType;
    private final Map<K, LongAccumulator> statMap;

    public StatCounterBundle(final Class<K> keyType) {
        this.keyType = Objects.requireNonNull(keyType);
        this.statMap = new EnumMap<>(keyType);
        forEach(keyType, k -> statMap.put(k, newAbsLongAccumulator()));
    }

    public void increment(final K stat) {
        increment(stat, 1);
    }

    public void increment(final K stat, final long amount) {
        statMap.get(stat).accumulate(amount);
    }

    public long get(final K stat) {
        final LongAccumulator longAdder = statMap.get(stat);
        return longAdder == null ? 0 : longAdder.longValue();
    }

    public Map<String, String> debugStats() {
        final NumberFormat numberFormat = NumberFormat.getNumberInstance();
        return statMap.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().name(),
                        entry -> numberFormat.format(entry.getValue().longValue())));
    }

    public static LongAccumulator newAbsLongAccumulator() {
        return new LongAccumulator(
                (left, right) -> {
                    final long newValue = left + right;
                    return newValue < 0 ? 0 : newValue;
                },
                0L);
    }

    public static <E extends Enum<E>> void forEach(final Class<E> enumClass, final Consumer<E> consumer) {
        EnumSet.allOf(enumClass).forEach(consumer);
    }
}

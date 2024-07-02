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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public final class JsonUtil {

    private static final Gson GENERIC_GSON = GsonJsonAdaptors.registerTypeAdapters(new GsonBuilder())
            .disableHtmlEscaping()
            .create();

    private static Gson getGson(final Flag... flags) {
        if (flags == null || flags.length == 0) {
            return GENERIC_GSON;
        }

        final GsonBuilder gsonBuilder = GsonJsonAdaptors.registerTypeAdapters(new GsonBuilder());

        return gsonBuilder.create();
    }

    public static <T> T deserialize(final String jsonString, final Class<T> classOfT) {
        return getGson().fromJson(jsonString, classOfT);
    }

    public static <T> String serialize(final T srcObject, final Flag... flags) {
        return getGson(flags).toJson(srcObject, unknownClassResolver(srcObject));
    }

    public enum Flag {
        PrettyPrint,
    }

    public static Instant parseIsoToInstant(final String input) {
        return Instant.parse(input);
    }

    public static String toIsoDate(final Instant instant) {
        return instant == null ? "" : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    static Class<?> unknownClassResolver(final Object srcObject) {
        if (srcObject instanceof List) {
            return List.class;
        } else if (srcObject instanceof SortedSet) {
            return SortedSet.class;
        } else if (srcObject instanceof Set) {
            return Set.class;
        } else if (srcObject instanceof SortedMap) {
            return SortedMap.class;
        } else if (srcObject instanceof Map) {
            return Map.class;
        }
        return srcObject.getClass();
    }
}

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import org.jetbrains.annotations.Nullable;

public final class JsonUtil {
    private static final Moshi GENERIC_MOSHI = getMoshi();

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

    public enum Flag {
        PrettyPrint,
    }

    private static Moshi getMoshi(final Flag... flags) {
        if (GENERIC_MOSHI != null && (flags == null || flags.length <= 0)) {
            return GENERIC_MOSHI;
        }

        final Moshi.Builder moshiBuilder = new Moshi.Builder();
        registerTypeAdapters(moshiBuilder, flags);
        return moshiBuilder.build();
    }

    public static <T> T deserialize(final String jsonString, final Class<T> classOfT) {
        return getGson().fromJson(jsonString, classOfT);
    }

    public static <T> String serialize(final T srcObject, final Flag... flags) {
        return getGson(flags).toJson(srcObject, unknownClassResolver(srcObject));
    }

    static void registerTypeAdapters(final Moshi.Builder moshiBuilder, final Flag... flags) {
        moshiBuilder.add(Instant.class, applyFlagsToAdapter(new InstantTypeAdapter(), flags));
    }

    static <T> JsonAdapter<T> applyFlagsToAdapter(final JsonAdapter<T> adapter, final Flag... flags) {
        final JsonAdapter<T> adapterInProgress = adapter;
        return adapterInProgress;
    }

    /**
     * Stores instants in ISO 8601 format, with a deserializer that also reads local-platform format reading.
     */
    private static class InstantTypeAdapter extends JsonAdapter<Instant> {
        @Nullable
        @Override
        public Instant fromJson(final JsonReader reader) throws IOException {
            final String strValue = reader.nextString();
            if (strValue == null || strValue.isBlank()) {
                return null;
            }

            try {
                return parseIsoToInstant(strValue);
            } catch (final Exception e) {
                throw new IllegalArgumentException("unable to parse stored json Instant.class timestamp '" + strValue
                        + "' error: " + e.getMessage());
            }
        }

        @Override
        public void toJson(final JsonWriter writer, @Nullable final Instant value) throws IOException {
            if (value == null) {
                writer.nullValue();
                return;
            }

            writer.jsonValue(toIsoDate(value));
        }
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

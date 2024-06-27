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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GsonJsonAdaptors {
    private static final Logger LOGGER = LoggerFactory.getLogger(GsonJsonAdaptors.class);

    static GsonBuilder registerTypeAdapters(final GsonBuilder gsonBuilder) {
        gsonBuilder.registerTypeAdapter(Instant.class, new InstantTypeAdapter());
        gsonBuilder.registerTypeAdapter(LongAdder.class, new LongAdderTypeAdaptor());
        gsonBuilder.registerTypeAdapter(Duration.class, new DurationAdaptor());
        return gsonBuilder;
    }

    /**
     *
     * /**
     * GsonSerializer that stores instants in ISO 8601 format, with a deserializer that also reads local-platform format reading.
     */
    private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        private InstantTypeAdapter() {}

        @Override
        public JsonElement serialize(
                final Instant instant, final Type type, final JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(toIsoDate(instant));
        }

        @Override
        public Instant deserialize(
                final JsonElement jsonElement,
                final Type type,
                final JsonDeserializationContext jsonDeserializationContext) {
            try {
                return parseIsoToInstant(jsonElement.getAsString());
            } catch (final Exception e) {
                LOGGER.debug("unable to parse stored json Instant.class timestamp '" + jsonElement.getAsString()
                        + "' error: " + e.getMessage());
                throw new JsonParseException(e);
            }
        }
    }

    private static class LongAdderTypeAdaptor implements JsonSerializer<LongAdder>, JsonDeserializer<LongAdder> {
        @Override
        public LongAdder deserialize(
                final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
                throws JsonParseException {
            final long longValue = json.getAsLong();
            final LongAdder longAdder = new LongAdder();
            longAdder.add(longValue);
            return longAdder;
        }

        @Override
        public JsonElement serialize(
                final LongAdder src, final Type typeOfSrc, final JsonSerializationContext context) {
            return new JsonPrimitive(src.longValue());
        }
    }

    private static class DurationAdaptor implements JsonSerializer<Duration>, JsonDeserializer<Duration> {
        @Override
        public Duration deserialize(
                final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
                throws JsonParseException {
            final String stringValue = json.getAsString();
            return Duration.parse(stringValue);
        }

        @Override
        public JsonElement serialize(final Duration src, final Type typeOfSrc, final JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }
    }

    public static Instant parseIsoToInstant(final String input) {
        return Instant.parse(input);
    }

    public static String toIsoDate(final Instant instant) {
        return instant == null ? "" : instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}

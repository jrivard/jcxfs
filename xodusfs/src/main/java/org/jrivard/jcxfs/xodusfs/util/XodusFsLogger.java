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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public class XodusFsLogger {
    private final org.slf4j.Logger slfLogger;

    public static XodusFsLogger getLogger(final Class<?> className) {
        return new XodusFsLogger(className.getName());
    }

    private XodusFsLogger(final String name) {
        final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        slfLogger = loggerFactory.getLogger(name);
    }

    public boolean isLevel(final Level logLevel) {
        if (logLevel == null || !slfLogger.isEnabledForLevel(logLevel)) {
            return false;
        }

        return slfLogger.isEnabledForLevel(logLevel);
    }

    public void trace(final Supplier<String> message) {
        doLog(slfLogger, Level.TRACE, null, message, null);
    }

    public void trace(final String message) {
        doLog(slfLogger, Level.TRACE, null, message);
    }

    public void debug(final Supplier<String> message) {
        doLog(slfLogger, Level.DEBUG, null, message, null);
    }

    public void debug(final Supplier<String> message, final Duration duration) {
        doLog(slfLogger, Level.DEBUG, null, message, duration);
    }

    public void debug(final Supplier<String> message, final Throwable throwable) {
        doLog(slfLogger, Level.DEBUG, throwable, message, null);
    }

    public void debug(final String message) {
        doLog(slfLogger, Level.DEBUG, null, message);
    }

    public void info(final Supplier<String> message) {
        doLog(slfLogger, Level.INFO, null, message, null);
    }

    public void info(final String message) {
        doLog(slfLogger, Level.INFO, null, message);
    }

    public void error(final String message) {
        doLog(slfLogger, Level.ERROR, null, message);
    }

    public void error(final Supplier<String> message, final Throwable t) {
        doLog(slfLogger, Level.ERROR, t, message, null);
    }

    public void error(final String message, final Throwable t) {
        doLog(slfLogger, Level.ERROR, t, message);
    }

    private static void doLog(
            final org.slf4j.Logger slf4jLogger, final Level logLevel, final Throwable throwable, final String message) {

        if (!slf4jLogger.isEnabledForLevel(logLevel)) {
            return;
        }

        final LoggingEventBuilder builder = slf4jLogger
                .makeLoggingEventBuilder(logLevel)
                .setMessage(message)
                .setCause(throwable);

        builder.log();
    }

    private static void doLog(
            final Logger slf4jLogger,
            final Level logLevel,
            final Throwable throwable,
            final Supplier<String> message,
            final Duration duration) {

        if (!slf4jLogger.isEnabledForLevel(logLevel)) {
            return;
        }

        final Supplier<String> effectiveMessage =
                duration == null ? message : () -> message.get() + " (" + duration.truncatedTo(ChronoUnit.MILLIS) + ")";

        slf4jLogger.makeLoggingEventBuilder(logLevel).setCause(throwable).log(effectiveMessage);
    }
}

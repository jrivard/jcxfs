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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcxfsLogger {
    private final org.slf4j.Logger logbackLogger;

    public static JcxfsLogger getLogger(final Class<?> className) {
        return new JcxfsLogger(className.getName());
    }

    private JcxfsLogger(final String name) {
        final ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        logbackLogger = loggerFactory.getLogger(name);
    }

    public boolean isLevel(final LogUtil.LogLevel logLevel) {
        if (logLevel == null || logLevel == LogUtil.LogLevel.none) {
            return false;
        }

        return logbackLogger.isEnabledForLevel(logLevel.getSlf4jLevel());
    }

    public void trace(final Supplier<String> message) {
        log(LogUtil.LogLevel.trace, null, message, null);
    }

    public void trace(final Supplier<String> message, final Duration duration) {
        log(LogUtil.LogLevel.trace, null, message, duration);
    }

    public void trace(final Supplier<String> message, final Throwable throwable) {
        log(LogUtil.LogLevel.trace, throwable, message, null);
    }

    public void debug(final Supplier<String> message) {
        log(LogUtil.LogLevel.debug, null, message, null);
    }

    public void debug(final Supplier<String> message, final Duration duration) {
        log(LogUtil.LogLevel.debug, null, message, duration);
    }

    public void debug(final Supplier<String> message, final Throwable throwable) {
        log(LogUtil.LogLevel.debug, throwable, message, null);
    }

    public void info(final Supplier<String> message) {
        log(LogUtil.LogLevel.info, null, message, null);
    }

    public void error(final Supplier<String> message) {
        log(LogUtil.LogLevel.error, null, message, null);
    }

    public void error(final Supplier<String> message, final Throwable t) {
        log(LogUtil.LogLevel.error, t, message, null);
    }

    private void log(
            final LogUtil.LogLevel logLevel,
            final Throwable throwable,
            final Supplier<String> message,
            final Duration duration) {
        pushToLog4j(logbackLogger, logLevel, throwable, message, duration);
    }

    private void pushToLog4j(
            final Logger slf4jLogger,
            final LogUtil.LogLevel logLevel,
            final Throwable throwable,
            final Supplier<String> message,
            final Duration duration) {

        if (!isLevel(logLevel)) {
            return;
        }

        final Supplier<String> effectiveMessage =
                duration == null ? message : () -> message.get() + " (" + duration.truncatedTo(ChronoUnit.MILLIS) + ")";

        slf4jLogger
                .makeLoggingEventBuilder(logLevel.getSlf4jLevel())
                .setCause(throwable)
                .log(effectiveMessage);
    }
}

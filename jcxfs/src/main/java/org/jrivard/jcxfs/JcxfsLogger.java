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

import java.util.function.Supplier;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

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
        log(LogUtil.LogLevel.trace, null, message);
    }

    public void trace(final Supplier<String> message, final Throwable throwable) {
        log(LogUtil.LogLevel.trace, throwable, message);
    }

    public void debug(final Supplier<String> message) {
        log(LogUtil.LogLevel.debug, null, message);
    }

    public void debug(final Supplier<String> message, final Throwable throwable) {
        log(LogUtil.LogLevel.debug, throwable, message);
    }

    public void debug(final String message) {
        log(LogUtil.LogLevel.debug, null, message);
    }

    public void info(final Supplier<String> message) {
        log(LogUtil.LogLevel.info, null, message);
    }

    public void info(final String message) {
        log(LogUtil.LogLevel.info, null, message);
    }

    public void error(final String message) {
        log(LogUtil.LogLevel.error, null, message);
    }

    public void error(final Supplier<String> message, final Throwable t) {
        log(LogUtil.LogLevel.error, t, message);
    }

    public void error(final String message, final Throwable t) {
        log(LogUtil.LogLevel.error, t, message);
    }

    private void log(final LogUtil.LogLevel logLevel, final Throwable throwable, final String message) {
        pushToLog4j(logbackLogger, logLevel, throwable, message);
    }

    private void log(final LogUtil.LogLevel logLevel, final Throwable throwable, final Supplier<String> message) {
        pushToLog4j(logbackLogger, logLevel, throwable, message);
    }

    private void pushToLog4j(
            final org.slf4j.Logger slf4jLogger,
            final LogUtil.LogLevel logLevel,
            final Throwable throwable,
            final String message) {

        if (!isLevel(logLevel)) {
            return;
        }

        final LoggingEventBuilder builder = slf4jLogger
                .makeLoggingEventBuilder(logLevel.getSlf4jLevel())
                .setMessage(message)
                .setCause(throwable);

        builder.log();
    }

    private void pushToLog4j(
            final Logger slf4jLogger,
            final LogUtil.LogLevel logLevel,
            final Throwable throwable,
            final Supplier<String> message) {

        if (!isLevel(logLevel)) {
            return;
        }

        slf4jLogger
                .makeLoggingEventBuilder(logLevel.getSlf4jLevel())
                .setCause(throwable)
                .log(message);
    }
}

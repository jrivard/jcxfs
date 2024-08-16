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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jrivard.jcxfs.cmd.PasswordString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public final class LogUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogUtil.class);

    public enum LogLevel {
        none(null, null),
        error(ch.qos.logback.classic.Level.ERROR, org.slf4j.event.Level.ERROR),
        info(Level.INFO, org.slf4j.event.Level.INFO),
        debug(Level.DEBUG, org.slf4j.event.Level.DEBUG),
        trace(Level.TRACE, org.slf4j.event.Level.TRACE),
        ;

        private final ch.qos.logback.classic.Level logbackLevel;
        private final org.slf4j.event.Level slf4jLevel;

        LogLevel(final ch.qos.logback.classic.Level logbackLevel, final org.slf4j.event.Level slf4jLevel) {
            this.logbackLevel = logbackLevel;
            this.slf4jLevel = slf4jLevel;
        }

        public ch.qos.logback.classic.Level getLogbackLevel() {
            return logbackLevel;
        }

        public org.slf4j.event.Level getSlf4jLevel() {
            return slf4jLevel;
        }

        public static class Converter implements CommandLine.ITypeConverter<LogLevel> {
            @Override
            public LogLevel convert(final String value) throws Exception {
                return Stream.of(LogLevel.values())
                        .filter(p -> value.equalsIgnoreCase(p.name()))
                        .findFirst()
                        .orElseThrow(() -> new CommandLine.TypeConversionException(usage()));
            }
        }

        private static String usage() {
            return "must be one of "
                    + Stream.of(LogLevel.values()).map(Enum::name).collect(Collectors.joining(","));
        }
    }

    public static void initLogging(final LogLevel logLevel, final String logFile) {

        if (logLevel != null && logLevel != LogLevel.none) {
            initLogback(logLevel, logFile);
        } else {
            final LoggerContext logCtx = (LoggerContext) LoggerFactory.getILoggerFactory();
            logCtx.reset();
        }

        LOGGER.info("starting jcxfs " + VersionUtil.version());
    }

    public static void logCli(final CommandLine cmd) {
        final StringBuilder sb = new StringBuilder();
        sb.append("options:").append(logOptions(cmd.getParseResult(), ""));
        final CommandLine.ParseResult parseResult = cmd.getParseResult();
        if (parseResult.hasSubcommand()) {
            sb.append("\nsubcommand [")
                    .append(parseResult.subcommand().commandSpec().name())
                    .append("] options:")
                    .append(logOptions(parseResult.subcommand(), "   "));
        }
        LOGGER.debug(sb.toString());
    }

    private static String logOptions(final CommandLine.ParseResult parseResult, final String linePrefix) {

        if (parseResult.matchedOptions().isEmpty()
                && parseResult.matchedPositionals().isEmpty()) {
            return " [none]";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("\n");

        {
            final Iterator<CommandLine.Model.PositionalParamSpec> paramIterator =
                    parseResult.matchedPositionals().iterator();
            while (paramIterator.hasNext()) {
                final CommandLine.Model.PositionalParamSpec option = paramIterator.next();
                final Object value;
                if (option.type().equals(PasswordString.class)) {
                    value = "***password***";
                } else {
                    value = option.getValue();
                }
                sb.append(linePrefix)
                        .append("<")
                        .append(option.index().toString())
                        .append(">: ")
                        .append(value);
                if (paramIterator.hasNext()) {
                    sb.append("\n");
                }
            }
        }

        {
            final Iterator<CommandLine.Model.OptionSpec> optionIterator =
                    parseResult.matchedOptions().iterator();
            while (optionIterator.hasNext()) {
                final CommandLine.Model.OptionSpec option = optionIterator.next();
                final Object value;
                if (option.type().equals(PasswordString.class)) {
                    value = "***password***";
                } else {
                    value = parseResult
                            .commandSpec()
                            .findOption(option.shortestName())
                            .getValue();
                }
                sb.append(linePrefix).append(option.longestName()).append(": ").append(value);
                if (optionIterator.hasNext()) {
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    public static void initLogback(final LogLevel logLevel, final String logFile) {

        System.setProperty("org.slf4j.simpleLogger.showInitializationMessages", "false");

        // Get the logger context
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getStatusManager().clear();
        context.putProperty("org.slf4j.simpleLogger.showInitializationMessages", "false");
        context.putProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

        final ch.qos.logback.classic.Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.getLoggerContext().getStatusManager().clear();

        // Create a console appender
        final OutputStreamAppender<ILoggingEvent> appender;
        if (logFile == null || logFile.isEmpty()) {
            appender = new ConsoleAppender<>();
        } else {
            final FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setAppend(true);
            fileAppender.setFile(logFile);
            appender = fileAppender;
        }
        appender.setContext(context);

        // Create a pattern layout encoder
        final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%date{ISO8601} %-5level %msg%n");
        encoder.setContext(context);
        encoder.start();
        appender.setEncoder(encoder);
        appender.start();

        appender.addFilter(new BlockUselessXodusExceptionFilter());

        // Set appender level to TRACE
        // Configure logger level through the logger context
        rootLogger.setLevel(logLevel.getLogbackLevel());
        rootLogger.detachAndStopAllAppenders();
        rootLogger.addAppender(appender);
    }

    // filter to block xodus exception that gets logged
    // https://youtrack.jetbrains.com/issue/XD-856/Xodus-2.0.1-logs-an-exception-stack-trace-during-startup-when-running-under-JDK-17
    private static class BlockUselessXodusExceptionFilter extends Filter<ILoggingEvent> {
        private static final String msg =
                "Unable to make public void sun.nio.ch.FileChannelImpl.setUninterruptible() accessible:"
                        + " module java.base does not \"exports sun.nio.ch\" to unnamed module";
        private static final Class<?> exceptionClass = InaccessibleObjectException.class;

        public BlockUselessXodusExceptionFilter() {}

        @Override
        public FilterReply decide(final ILoggingEvent event) {
            final IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy == null) {
                return FilterReply.NEUTRAL;
            }

            if (!(throwableProxy instanceof final ThrowableProxy throwableProxyImpl)) {
                return FilterReply.NEUTRAL;
            }

            final Throwable throwable = throwableProxyImpl.getThrowable();
            if (exceptionClass.isInstance(throwable) && throwable.getMessage().startsWith(msg)) {
                return FilterReply.DENY;
            }

            return FilterReply.NEUTRAL;
        }
    }
}

package com.solace.performance;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Streams Reflowing 2.17 logs a diagnostic line at INFO for every terrain prefetch (SR-D0057 ...),
 * dozens per minute while exploring, which costs log I/O on its worker threads and buries real errors.
 * Log Begone only filters loggers that have their own log4j entry, and Streams logs through the root
 * config, so this filter sits on the root logger and drops only INFO/DEBUG lines from the
 * "StreamsReflowing" logger whose text starts with "SR-". Warnings and errors always pass.
 * Disable with -Dsolace.quietStreamsDiagnostics=false.
 */
public final class LogNoiseFilter extends AbstractFilter {
    private static final String STREAMS_LOGGER = "StreamsReflowing";

    private LogNoiseFilter() {
        super(Result.DENY, Result.NEUTRAL);
    }

    public static void install() {
        if (!Boolean.parseBoolean(System.getProperty("solace.quietStreamsDiagnostics", "true"))) return;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            LoggerConfig root = context.getConfiguration().getRootLogger();
            LogNoiseFilter filter = new LogNoiseFilter();
            filter.start();
            root.addFilter(filter);
            context.updateLoggers();
        } catch (RuntimeException | LinkageError e) {
            LogManager.getLogger("Solace/LogNoise").warn("Could not install Streams diagnostics filter", e);
        }
    }

    @Override
    public Result filter(LogEvent event) {
        if (event.getLevel().isMoreSpecificThan(Level.WARN)) return Result.NEUTRAL;
        if (!STREAMS_LOGGER.equals(event.getLoggerName())) return Result.NEUTRAL;
        String text = event.getMessage().getFormattedMessage();
        return text != null && text.startsWith("SR-") ? Result.DENY : Result.NEUTRAL;
    }
}

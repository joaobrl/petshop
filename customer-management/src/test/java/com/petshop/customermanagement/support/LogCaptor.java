package com.petshop.customermanagement.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LogCaptor implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCaptor(Class<?> source) {
        this.logger = (Logger) LoggerFactory.getLogger(source);
        this.appender.start();
        this.logger.addAppender(appender);
    }

    public static LogCaptor of(Class<?> source) {
        return new LogCaptor(source);
    }

    public List<ILoggingEvent> events() {
        return appender.list;
    }

    public boolean has(Level level, String fragment) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}

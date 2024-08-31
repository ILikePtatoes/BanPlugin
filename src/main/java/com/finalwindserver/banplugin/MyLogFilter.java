package com.finalwindserver.banplugin;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import javax.annotation.Nullable;

public class MyLogFilter extends AbstractFilter {

    private static final String STRING_WE_DISLIKE = "Disconnecting ";
    private static final String ANOTHER_STRING_WE_DISLIKE = " lost connection";

    private static final boolean USE_RAW_STRING = false;


    public MyLogFilter() {
        super(Filter.Result.DENY, Filter.Result.NEUTRAL);
    }
    private Result doFilter(@Nullable String message) {
        if (message == null) {
            return onMismatch;
        }
        if (message.contains(STRING_WE_DISLIKE) || message.contains(ANOTHER_STRING_WE_DISLIKE)) {
            return onMatch;
        }
        return onMismatch;
    }

    @Override
    public Result filter(LogEvent event) {
        Message msg = event == null ? null : event.getMessage();
        String message = msg == null ? null : (USE_RAW_STRING ? msg.getFormat() : msg.getFormattedMessage());
        return doFilter(message);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return doFilter(msg == null ? null : msg.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return doFilter(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        String message = msg == null ? null : (USE_RAW_STRING ? msg.getFormat() : msg.getFormattedMessage());
        return doFilter(message);
    }
}


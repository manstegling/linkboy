package se.motility.linkboy.util;

import java.lang.Thread.UncaughtExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotUncaughtExceptionHandler implements UncaughtExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception encountered. Thread will terminate.", e);
    }

}

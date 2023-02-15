package se.motility.linkboy.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Util {

    private static final Logger LOG = LoggerFactory.getLogger(Util.class);

    public static int parseMovieId(String str, String fieldName) {
        try {
            int i = Integer.parseInt(str.trim());
            if (i < 0) {
                throw new IllegalArgumentException("negative value not allowed");
            }
            return i;
        } catch (Exception e) {
            LOG.error("Invalid input {} for field {}", str, fieldName);
            throw new IllegalArgumentException(fieldName + " must be a non-negative integer but was " + str);
        }
    }

    private Util() {
        throw new UnsupportedOperationException("Do not instantiate utility class");
    }

}

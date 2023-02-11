package se.motility.linkboy.util;

import java.io.IOException;

/**
 * Convenience interface for handling checked IOExceptions e.g. in lambdas
 *
 * @param <T> the type of the supplied result
 */
public interface IOExceptionThrowingSupplier<T> {

    T get() throws IOException;

}

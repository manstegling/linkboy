package se.motility.linkboy.util;

import java.io.IOException;

public interface IOExceptionThrowingSupplier<T> {

    T get() throws IOException;

}

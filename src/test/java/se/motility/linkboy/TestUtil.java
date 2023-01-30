package se.motility.linkboy;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

public class TestUtil {

    public static InputStream open(String path, boolean isGzip) throws IOException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        return isGzip ? new GZIPInputStream(in,4096) : in;
    }

}

package net.corda.node;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class CordaURLStreamHandler extends URLStreamHandler {
    private CordaJarLoader jarLoader;

    CordaURLStreamHandler(CordaJarLoader loader) {
        jarLoader = loader;
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        System.out.println("Creating connection");
        return new CordaJarJarURLConnection(u, jarLoader);
    }
}

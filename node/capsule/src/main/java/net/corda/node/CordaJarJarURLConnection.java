package net.corda.node;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class CordaJarJarURLConnection extends URLConnection {
    private String targetName;
    private CordaJarLoader jarLoader;

    CordaJarJarURLConnection(URL u, CordaJarLoader loader) throws MalformedURLException {
        super(u);
        jarLoader = loader;
        targetName = getTargetFileName();
    }

    @Override
    public void connect() throws IOException {
        System.out.println("Connection started");
        connected = true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        System.out.println("Getting input stream");
        System.out.println("JAR LOADER: " + jarLoader);
        return jarLoader.findResourceStream(targetName);
    }

    private String getTargetFileName() throws MalformedURLException {
        String[] parts = url.toExternalForm().split(":");
        if(parts.length == 3) {
            return parts[2];
        } else {
            throw new MalformedURLException("Corda Jar Jar URLs require corda:jarjar prefix");
        }
    }
}

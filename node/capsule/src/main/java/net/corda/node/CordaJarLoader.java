package net.corda.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

public class CordaJarLoader extends ClassLoader {
    private HashMap<String, String> resources = new HashMap<String, String>();
    private JarFile jarFile;

    private CordaJarLoader(JarFile file) {
        jarFile = file;
        scan(jarFile);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String className = name.replace('.', '/').concat(".class");
        if (resources.containsKey(className)) {
            try {
                byte[] bytes = getClassBytes(className);
                return defineClass(bytes, 0, bytes.length);
            } catch (IOException e) {
                System.err.println(e);
            } catch (ClassFormatError e) {
                System.err.println(e);
            }
        }

        throw new ClassNotFoundException();
    }

    @Override
    protected URL findResource(String name) {
        System.out.println("Finding resource: " + name);
        if (resources.containsKey(name)) {
            try {
                System.out.println("Found resource");
                String container = resources.get(name);
                return new URL(null, "corda:jarjar:" + name, new CordaURLStreamHandler(this));
            } catch (MalformedURLException e) {
                System.err.println(e);
            }
        }

        return super.findResource(name);
    }

    public InputStream findResourceStream(String name) throws IOException {
        System.out.println("Resource: " + name);
        JarStreamAndEntry streamAndEntry = getResourceSteamAndEntry(name);
        System.out.println("StreamAndEntry: " + streamAndEntry);
        return (streamAndEntry != null) ? streamAndEntry.stream : null;
    }

    private class JarStreamAndEntry {
        JarInputStream stream;
        JarEntry entry;

        JarStreamAndEntry(JarInputStream stream, JarEntry entry) {
            this.stream = stream;
            this.entry = entry;
        }
    }

    private JarStreamAndEntry getResourceSteamAndEntry(String name) throws IOException {
        System.out.println("Looking for STREAM AND ENTRY " + name);
        String jarEntry = resources.get(name);
        InputStream is = jarFile.getInputStream(jarFile.getJarEntry(jarEntry));
        JarInputStream jis = new JarInputStream(is);
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.getName().equals(name)) {
                return new JarStreamAndEntry(jis, entry);
            }
        }

        return null;
    }

    private byte[] getClassBytes(String name) throws IOException, ClassNotFoundException {
        JarStreamAndEntry streamAndEntry = getResourceSteamAndEntry(name);
        if(streamAndEntry != null) {
            return readBytes(streamAndEntry.entry, streamAndEntry.stream);
        }

        throw new ClassNotFoundException("Class " + name + " does not exist in this classloader");
    }

    private byte[] readBytes(JarEntry entry, JarInputStream jis) throws IOException {
        if (entry.getSize() >= 0) {
            return readBytesWithLength(entry, jis);
        } else {
            return readBytesWithBuffer(entry, jis);
        }
    }

    private byte[] readBytesWithLength(JarEntry entry, JarInputStream jis) throws IOException {
        byte[] bytes = new byte[(int) entry.getSize()];
        int total = 0;

        do {
            total += jis.read(bytes, total, bytes.length - total);
        } while(total < bytes.length);

        return bytes;
    }

    private byte[] readBytesWithBuffer(JarEntry entry, JarInputStream jis) throws IOException {
        // TODO: Work out a more sensible method to do this
        byte[] bytes = new byte[1024 * 1024];
        int total = 0;

        do {
            int read = jis.read(bytes, total, bytes.length - total);
            total += read;
        } while(jis.available() > 0);

        return Arrays.copyOfRange(bytes, 0, total + 1);
    }

    private void scan(JarFile file) {
        Enumeration<? extends JarEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (hasJarExtention(entry.getName())) {
                try {
                    scan(entry.getName(), file.getInputStream(entry));
                } catch (IOException e) {
                    System.err.println(e);
                }
            }
        }
    }

    private void scan(String bucketName, InputStream is) throws IOException {
        JarInputStream jis = new JarInputStream(is);
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            resources.put(entry.getName(), bucketName);
        }
    }

    private static boolean hasJarExtention(String filename) {
        String[] parts = filename.split("\\.");
        return parts[parts.length - 1].equals("jar");
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        File maybeJar = new File(CordaJarLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (maybeJar.isFile() && hasJarExtention(maybeJar.getName())) {
            ClassLoader loader = new CordaJarLoader(new JarFile(maybeJar));
            Class<?> startupClass = loader.loadClass("net.corda.node.Corda");
            Method m = startupClass.getMethod("main", String[].class);

            long timeTaken = System.currentTimeMillis() - startTime;
            System.out.println("Time taken: " + timeTaken + "ms");

            m.invoke(null, new Object[] { args });
        }
    }
}

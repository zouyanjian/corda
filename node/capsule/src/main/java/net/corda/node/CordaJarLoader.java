package net.corda.node;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
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

    //@Override
    //protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    //    try {
    //        Class<?> maybeClass = findLoadedClass(name);
    //        return (maybeClass == null) ? loadClassInternal(name, resolve) : maybeClass;
    //    } catch (ClassNotFoundException e) {
    //        return super.loadClass(name, resolve);
    //    }
    //}

    private byte[] getClassBytes(String name) throws IOException, ClassNotFoundException {
        String jarEntry = resources.get(name);
        InputStream is = jarFile.getInputStream(jarFile.getJarEntry(jarEntry));
        JarInputStream jis = new JarInputStream(is);
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.getName().equals(name)) {
                return readBytes(entry, jis);
            }
        }

        throw new ClassNotFoundException("Class " + name + " does not exist in this classloader");
    }

    private byte[] readBytes(JarEntry entry, JarInputStream jis) throws IOException {
        System.out.println("Entry: " + entry.getName() + ", size: " + entry.getSize());
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

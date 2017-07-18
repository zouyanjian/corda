package net.corda.node;

import net.corda.node.internal.NodeStartup;

import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CordaJarLoader extends ClassLoader {

    CordaJarLoader(ZipFile file) {
        Enumeration<? extends ZipEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (hasZipExtention(entry.getName())) {
                System.out.println(entry.getName());
            }
        }
    }

    static boolean hasZipExtention(String filename) {
        String[] parts = filename.split("\\.");
        return parts[parts.length - 1].equals("jar");
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();
        File maybeJar = new File(NodeStartup.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (maybeJar.isFile() && hasZipExtention(maybeJar.getName())) {
            new CordaJarLoader(new ZipFile(maybeJar));
        }

        long timeTaken = System.currentTimeMillis() - startTime;
        System.out.println("Time taken: " + timeTaken + "ms");
    }
}

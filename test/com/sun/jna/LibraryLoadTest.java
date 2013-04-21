/* Copyright (c) 2007-20013 Timothy Wall, All Rights Reserved
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.  
 */
package com.sun.jna;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import junit.framework.TestCase;

public class LibraryLoadTest extends TestCase implements Paths {
    
    private class TestLoader extends URLClassLoader {
        public TestLoader(File path) throws MalformedURLException {
            super(new URL[] { path.toURI().toURL() }, null);
        }
    }

    public void testLoadJNALibrary() {
        assertTrue("Pointer size should never be zero", Pointer.SIZE > 0);
    }
    
    public void testLoadJAWT() {
        if (!Platform.HAS_AWT) return;

        if (GraphicsEnvironment.isHeadless()) return;

        // Encapsulate in a separate class to avoid class loading issues where
        // AWT is unavailable
        AWT.loadJAWT(getName());
    }
    
    public void testLoadAWTAfterJNA() {
        if (!Platform.HAS_AWT) return;

        if (GraphicsEnvironment.isHeadless()) return;

        if (Pointer.SIZE > 0) {
            Toolkit.getDefaultToolkit();
        }
    }
    
    public void testLoadFromJNALibraryPath() {
        // Tests are already configured to load from this path
        NativeLibrary.getInstance("testlib");
    }

    public void testLoadFromClasspath() throws MalformedURLException {
        NativeLibrary.getInstance("testlib-path", new TestLoader(new File(TESTPATH)));
    }

    public void testLoadFromClasspathAbsolute() throws MalformedURLException {
        String name = NativeLibrary.mapSharedLibraryName("testlib-path");
        NativeLibrary.getInstance("/" + name, new TestLoader(new File(TESTPATH)));
    }

    public void testLoadFromJar() throws MalformedURLException {
        NativeLibrary.getInstance("testlib-jar", new TestLoader(new File(TESTJAR)));
    }

    public void testLoadFromJarAbsolute() throws MalformedURLException {
        String name = NativeLibrary.mapSharedLibraryName("testlib-jar");
        NativeLibrary.getInstance("/" + name, new TestLoader(new File(TESTJAR)));
    }

    public void testLoadExplicitAbsolutePath() throws MalformedURLException {
        NativeLibrary.getInstance(new File(TESTPATH, "testlib-truncated").getAbsolutePath());
    }

    public static interface CLibrary extends Library {
        int wcslen(WString wstr);
        int strlen(String str);
        int atol(String str);

        Pointer getpwuid(int uid);
        int geteuid();
    }

    private Object load() {
        return Native.loadLibrary(Platform.C_LIBRARY_NAME, CLibrary.class);
    }
    
    public void testLoadCLibrary() {
        load();
    }
    
    private void copy(File src, File dst) throws Exception {
        FileInputStream is = new FileInputStream(src);
        FileOutputStream os = new FileOutputStream(dst);
        int count;
        byte[] buf = new byte[1024];
        try {
            while ((count = is.read(buf, 0, buf.length)) > 0) {
                os.write(buf, 0, count);
            }
        }
        finally {
            try { is.close(); } catch(IOException e) { }
            try { os.close(); } catch(IOException e) { } 
        }
    }

    public void testLoadLibraryWithUnicodeName() throws Exception {
        File tmpdir = Native.getTempDir();
        String libName = NativeLibrary.mapSharedLibraryName("testlib");
        File src = new File(TESTPATH, libName);
        if (Platform.isWindowsCE()) {
            src = new File("/Storage Card", libName);
        }
        assertTrue("Expected JNA native library at " + src + " is missing", src.exists());

        final String UNICODE = "\u0444\u043b\u0441\u0432\u0443";

        String newLibName = libName.replace("testlib", UNICODE);
        File dst = new File(tmpdir, newLibName);
        dst.deleteOnExit();
        copy(src, dst);
        NativeLibrary.getInstance(UNICODE, new TestLoader(tmpdir));
    }
    
    public void testHandleObjectMethods() {
        CLibrary lib = (CLibrary)load();
        String method = "toString";
        try {
            lib.toString();
            method = "hashCode";
            lib.hashCode();
            method = "equals";
            lib.equals(null);
        }
        catch(UnsatisfiedLinkError e) {
            fail("Object method '" + method + "' not handled");
        }
    }

    public interface TestLib2 extends Library {
        int dependentReturnFalse();
    }

    // Only desktop windows provides an altered search path, looking for
    // dependent libraries in the same directory as the original
    public void testLoadDependentLibraryWithAlteredSearchPath() {
        try {
            TestLib2 lib = (TestLib2)Native.loadLibrary("testlib2", TestLib2.class);
            lib.dependentReturnFalse();
        }
        catch(UnsatisfiedLinkError e) {
            // failure expected on anything but windows
            if (Platform.isWindows() && !Platform.isWindowsCE()) {
                fail("Failed to load dependent libraries: " + e);
            }
        }
    }

    // Ubuntu bug when arch-specific libc is active
    // Only fails on *some* functions
    public void testLoadProperCLibraryVersion() {
        if (Platform.isWindows()) return;

        CLibrary lib = (CLibrary)Native.loadLibrary("c", CLibrary.class);
        assertNotNull("Couldn't get current user",
                      lib.getpwuid(lib.geteuid()));
    }
    
    private static class AWT {
        public static void loadJAWT(String name) {
            Frame f = new Frame(name);
            f.pack();
            try {
                // FIXME: this works as a test, but fails in ShapedWindowDemo
                // if the JAWT load workaround is not used
                Native.getWindowPointer(f);
            }
            finally {
                f.dispose();
            }
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(LibraryLoadTest.class);
    }
}

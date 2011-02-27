package org.jetbrains.ether;

import org.jetbrains.jps.Module;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 03.12.10
 * Time: 19:39
 * To change this template use File | Settings | File Templates.
 */
public class Reporter {
    public static final String myOkFlag = ".jps.ok";
    public static final String myFailFlag = ".jps.fail";

    private static String getSafePath (final String path) {
        final File f = new File(path);

        if (! f.exists()) {
            f.mkdir();
        }

        return path;
    }

    private static String getOkFlag(final Module m) {
        return getSafePath (m.getOutputPath()) + File.separator + myOkFlag;
    }

    private static String getFailFlag(final Module m) {
        return getSafePath (m.getOutputPath()) + File.separator + myFailFlag;
    }

    private static String getOkTestFlag(final Module m) {
        return getSafePath (m.getTestOutputPath()) + File.separator + myOkFlag;
    }

    private static String getFailTestFlag(final Module m) {
        return getSafePath(m.getTestOutputPath()) + File.separator + myFailFlag;
    }

    private static void write(final String name, final String contents) {
        try {
            final FileWriter writer = new FileWriter(name);

            writer.write(contents);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reportBuildSuccess(final Module m, final boolean tests) {
        write(getOkFlag(m), "dummy");
        if (tests) {
            write(getOkTestFlag(m), "dummy");
        }
    }

    public static void reportBuildFailure(final Module m, final boolean tests, final String reason) {
        write(getFailFlag(m), reason);
        if (tests) {
            write(getFailTestFlag(m), reason);
        }
    }

    public static boolean failureReported (final Module m, final boolean tests) {
        final File o = new File(getFailFlag(m));
        final File t = new File(getFailTestFlag(m));

        return o.exists() || (tests && t.exists());
    }
}

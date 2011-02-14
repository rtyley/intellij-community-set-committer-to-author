package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 2:03
 * To change this template use File | Settings | File Templates.
 */
public class StringCache {

    public static class S implements Comparable<S> {
        public final int index;
        public final String value;

        private S (final int i, final String v) {
            index = i;
            value = v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            S s = (S) o;

            if (index != s.index) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return index;
        }

        public int compareTo(S o) {
            return index - o.index;
        }
    }

    private static final Map<String, S> map = new HashMap<String, S> ();
    private static int index = 0;

    public static S get (final String s) {
        S r = map.get(s);

        if (r == null) {
            r = new S (index++, s);
            map.put(s, r);
        }

        return r;
    }

    public static S[] get (final String[] s) {
        if (s == null) {
            return null;
        }

        final S[] r = new S[s.length];

        for (int i = 0; i<r.length; i++)
            r[i] = get (s[i]);

        return r;
    }

    public static RW.ToWritable<S> fromS = new RW.ToWritable<S> () {
        public RW.Writable convert(S x) {
            return RW.fromString.convert(x.value);
        }
    };
}

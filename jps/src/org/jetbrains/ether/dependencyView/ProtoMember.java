package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 07.03.11
 * Time: 19:54
 * To change this template use File | Settings | File Templates.
 */
public abstract class ProtoMember extends Proto {
    public final TypeRepr.AbstractType type;
    public final Object value;

    protected ProtoMember(final int access, final String signature, final StringCache.S name, final TypeRepr.AbstractType t, final Object value) {
        super(access, signature, name);
        this.type = t;
        this.value = value;
    }

    private static Object readTyped(final BufferedReader r, final String tag) {
        if (tag.equals("string")) {
            return RW.readEncodedString(r);
        }

        if (tag.equals("none")) {
            return null;
        }

        final String val = RW.readString(r);

        if (tag.equals("integer"))
            return Integer.parseInt(val);

        if (tag.equals("long"))
            return Long.parseLong(val);

        if (tag.equals("float"))
            return Float.parseFloat(val);

        if (tag.equals("double"))
            return Double.parseDouble(val);

        return null;
    }

    protected ProtoMember(final BufferedReader r) {
        super(r);
        type = TypeRepr.reader.read(r);
        value = readTyped(r, RW.readString(r));
    }

    public void write(final BufferedWriter w) {
        super.write(w);
        type.write(w);

        if (value instanceof String) {
            RW.writeln(w, "string");
            RW.writeEncodedString(w, (String) value);
        } else if (value instanceof Integer) {
            RW.writeln(w, "integer");
            RW.writeln(w, value.toString());
        } else if (value instanceof Long) {
            RW.writeln(w, "long");
            RW.writeln(w, value.toString());
        } else if (value instanceof Float) {
            RW.writeln(w, "float");
            RW.writeln(w, value.toString());
        } else if (value instanceof Double) {
            RW.writeln(w, "double");
            RW.writeln(w, value.toString());
        } else {
            RW.writeln(w, "none");
        }
    }

    public Difference difference(final Proto past) {
        final ProtoMember m = (ProtoMember) past;
        int diff = super.difference(past).base();

        if (!m.type.equals(type)) {
            diff |= Difference.TYPE;
        }

        switch ((value == null ? 0 : 1) + (m.value == null ? 0 : 2)) {
            case 3:
                if (!value.equals(m.value)) {
                    diff |= Difference.VALUE;
                }
                break;

            case 2:
            case 1:
                diff |= Difference.VALUE;
                break;

            case 0:
                break;
        }

        return Difference.createBase(diff);
    }
}

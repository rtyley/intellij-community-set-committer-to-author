package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.Pair;
import org.jetbrains.asm4.Opcodes;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.03.11
 * Time: 17:39
 * To change this template use File | Settings | File Templates.
 */
abstract class Difference {
  public static boolean isPackageLocal(final int access) {
    return (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) == 0;
  }

  public static boolean weakerAccess(final int me, final int then) {
    return ((me & Opcodes.ACC_PRIVATE) > 0 && (then & Opcodes.ACC_PRIVATE) == 0) ||
           ((me & Opcodes.ACC_PROTECTED) > 0 && (then & Opcodes.ACC_PUBLIC) > 0) ||
           (isPackageLocal(me) && (then & Opcodes.ACC_PROTECTED) > 0);
  }

  public static final int NONE = 0;
  public static final int ACCESS = 1;
  public static final int TYPE = 2;
  public static final int VALUE = 4;
  public static final int SIGNATURE = 8;
  public static final int SUPERCLASS = 16;

  public interface Specifier<T> {
    Collection<T> added();

    Collection<T> removed();

    Collection<Pair<T, Difference>> changed();

    boolean unchanged();
  }

  public static <T> Specifier<T> make(final Set<T> past, final Set<T> now) {
    if (past == null) {
      final Collection<T> _now = Collections.unmodifiableCollection(now);
      return new Specifier<T>() {
        public Collection<T> added() {
          return _now;
        }

        public Collection<T> removed() {
          return Collections.emptyList();
        }

        public Collection<Pair<T, Difference>> changed() {
          return Collections.emptyList();
        }

        public boolean unchanged() {
          return false;
        }
      };
    }

    final Set<T> added = new HashSet<T>(now);

    added.removeAll(past);

    final Set<T> removed = new HashSet<T>(past);

    removed.removeAll(now);

    final Set<Pair<T, Difference>> changed = new HashSet<Pair<T, Difference>>();
    final Set<T> intersect = new HashSet<T>(past);
    final Map<T, T> nowMap = new HashMap<T, T>();

    for (T s : now) {
      if (intersect.contains(s)) {
        nowMap.put(s, s);
      }
    }

    intersect.retainAll(now);

    for (T x : intersect) {
      final T y = nowMap.get(x);

      if (x instanceof Proto) {
        final Proto px = (Proto)x;
        final Proto py = (Proto)y;
        final Difference diff = py.difference(px);

        if (!diff.no()) {
          changed.add(new Pair<T, Difference>(x, diff));
        }
      }
    }

    return new Specifier<T>() {
      public Collection<T> added() {
        return added;
      }

      public Collection<T> removed() {
        return removed;
      }

      public Collection<Pair<T, Difference>> changed() {
        return changed;
      }

      public boolean unchanged() {
        return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
      }
    };
  }

  public abstract int base();

  public abstract boolean no();

  public abstract boolean weakedAccess();

  public abstract int addedModifiers();

  public abstract int removedModifiers();

  public abstract boolean packageLocalOn();

  public abstract boolean hadValue();
}

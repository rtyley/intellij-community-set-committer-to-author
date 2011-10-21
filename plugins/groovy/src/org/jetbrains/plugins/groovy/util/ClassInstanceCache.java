package org.jetbrains.plugins.groovy.util;

import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author Sergey Evdokimov
 */
public class ClassInstanceCache {

  private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

  private ClassInstanceCache() {
  }

  @SuppressWarnings("unchecked")
  public static <T> T getInstance(@NotNull String className, ClassLoader classLoader) {
    Object res = CACHE.get(className);
    if (res == null) {
      try {
        res = classLoader.loadClass(className).newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      Object oldValue = CACHE.putIfAbsent(className, res);
      if (oldValue != null) {
        res = oldValue;
      }
    }

    return (T)res;
  }
}

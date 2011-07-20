/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.mac.foundation;

import com.intellij.util.containers.HashMap;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * @author spleaner
 */
public class Foundation {
  private static final FoundationLibrary myFoundationLibrary;

  static {
    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    Map<String, Object> foundationOptions = new HashMap<String, Object>();
    //foundationOptions.put(Library.OPTION_TYPE_MAPPER, FoundationTypeMapper.INSTANCE);

    myFoundationLibrary = (FoundationLibrary)Native.loadLibrary("Foundation", FoundationLibrary.class, foundationOptions);
  }

  static Callback ourRunnableCallback;


  public static void init() { /* fake method to init foundation */ }

  private Foundation() {
  }

  /**
   * Get the ID of the NSClass with className
   */
  public static ID getClass(String className) {
    return myFoundationLibrary.objc_getClass(className);
  }

  public static Pointer createSelector(String s) {
    return myFoundationLibrary.sel_registerName(s);
  }

  public static ID invoke(final ID id, final Pointer selector, Object... args) {
    return myFoundationLibrary.objc_msgSend(id, selector, args);
  }

  public static ID invoke(final String cls, final String selector, Object... args) {
    return invoke(getClass(cls), createSelector(selector), args);
  }

  public static ID invoke(final ID id, final String selector, Object... args) {
    return invoke(id, createSelector(selector), args);
  }

  public static ID registerObjcClass(ID superCls, String name) {
    return myFoundationLibrary.objc_allocateClassPair(superCls, name, 0);
  }

  public static void registerObjcClassPair(ID cls) {
    myFoundationLibrary.objc_registerClassPair(cls);
  }

  public static boolean isClassRespondsToSelector(ID cls, Pointer selectorName) {
    return myFoundationLibrary.class_respondsToSelector(cls, selectorName);
  }

  public static boolean addMethod(ID cls, Pointer selectorName, Callback impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }

  public static Pointer getClass(Pointer clazz) {
    return myFoundationLibrary.objc_getClass(clazz);
  }

  public static String fullUserName() {
    return toStringViaUTF8(myFoundationLibrary.NSFullUserName());
  }

  public static boolean isPackageAtPath(@NotNull final String path) {
    final ID workspace = invoke("NSWorkspace", "sharedWorkspace");
    final ID result = invoke(workspace, createSelector("isFilePackageAtPath:"), cfString(path));

    return result.intValue() == 1;
  }

  public static boolean isPackageAtPath(@NotNull final File file) {
    if (!file.isDirectory()) return false;
    return isPackageAtPath(file.getPath());
  }

  /**
   * Return a CFString as an ID, toll-free bridged to NSString.
   * <p/>
   * Note that the returned string must be freed with {@link #cfRelease(ID)}.
   */
  public static Pointer cfString(String s) {
    // Use a byte[] rather than letting jna do the String -> char* marshalling itself.
    // Turns out about 10% quicker for long strings.
    try {
      byte[] utf16Bytes = s.getBytes("UTF-16LE");
      return myFoundationLibrary.CFStringCreateWithBytes(null, utf16Bytes, utf16Bytes.length, 0x14000100,
                                                         (byte)0); /* kTextEncodingUnicodeDefault + kUnicodeUTF16LEFormat */
    }
    catch (UnsupportedEncodingException x) {
      throw new RuntimeException(x);
    }
  }

  public static String toStringViaUTF8(ID cfString) {
    int lengthInChars = myFoundationLibrary.CFStringGetLength(cfString);
    int potentialLengthInBytes = 3 * lengthInChars + 1; // UTF8 fully escaped 16 bit chars, plus nul

    byte[] buffer = new byte[potentialLengthInBytes];
    byte ok = myFoundationLibrary.CFStringGetCString(cfString, buffer, buffer.length, 0x08000100);
    if (ok == 0) throw new RuntimeException("Could not convert string");
    return Native.toString(buffer);
  }

  public static void cfRetain(ID id) {
    myFoundationLibrary.CFRetain(id);
  }

  private static Map<String, RunnableInfo> ourMainThreadRunnables = new HashMap<String, RunnableInfo>();
  private static long ourCurrentRunnableCount = 0;
  private static final Object RUNNABLE_LOCK = new Object();

  static class RunnableInfo {
    RunnableInfo(Runnable runnable, boolean useAutoreleasePool) {
      myRunnable = runnable;
      myUseAutoreleasePool = useAutoreleasePool;
    }
    Runnable myRunnable;
    boolean myUseAutoreleasePool;
  }

  public static void executeOnMainThread(final Runnable runnable, final boolean withAutoreleasePool, final boolean waitUntilDone) {
    initRunnableSupport();

    synchronized (RUNNABLE_LOCK) {
      ourCurrentRunnableCount++;
      ourMainThreadRunnables.put(String.valueOf(ourCurrentRunnableCount), new RunnableInfo(runnable, withAutoreleasePool));
    }

    final ID ideaRunnable = getClass("IdeaRunnable");
    final ID runnableObject = invoke(ideaRunnable, "alloc");
    invoke(runnableObject, "performSelectorOnMainThread:withObject:waitUntilDone:", createSelector("run:"),
           cfString(String.valueOf(ourCurrentRunnableCount)), Boolean.valueOf(waitUntilDone));
    invoke(runnableObject, "release");
  }

  private static void initRunnableSupport() {
    if (ourRunnableCallback == null) {
      final ID runnableClass = registerObjcClass(getClass("NSObject"), "IdeaRunnable");
      registerObjcClassPair(runnableClass);

      final Callback callback = new Callback() {
        public void callback(ID self, String selector, ID keyObject) {
          final String key = toStringViaUTF8(keyObject);


          RunnableInfo info;
          synchronized (RUNNABLE_LOCK) {
            info = ourMainThreadRunnables.remove(key);
          }

          if (info == null) return;


          ID pool = null;
          try {
            if (info.myUseAutoreleasePool) {
              pool = invoke("NSAutoreleasePool", "new");
            }

            info.myRunnable.run();
          }
          finally {
            if (pool != null) {
              invoke(pool, "release");
            }
          }
        }
      };
      if (!addMethod(runnableClass, createSelector("run:"), callback, "v*")) {
        throw new RuntimeException("Unable to add method to objective-c runnableClass class!");
      }
      ourRunnableCallback = callback;
    }
  }
}

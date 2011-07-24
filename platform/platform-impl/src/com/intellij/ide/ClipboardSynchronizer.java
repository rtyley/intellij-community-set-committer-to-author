/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.Patches;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.IntegerType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.datatransfer.DataTransferer;

import java.awt.*;
import java.awt.datatransfer.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

/**
 * <p>This class is used to workaround the problem with getting clipboard contents (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143).
 * Although this bug is marked as fixed actually Sun just set 10 seconds timeout for {@link java.awt.datatransfer.Clipboard#getContents(Object)}
 * method which may cause unacceptably long UI freezes. So we worked around this as follows:
 * <ul>
 *   <li>for Macs we use native method calls to access system clipboard lock-free;</li>
 *   <li>for Linux we temporary set short timeout and check for available formats (which should be fast if a clipboard owner is alive).</li>
 * </ul>
 * </p>
 *
 * @author nik
 */
public class ClipboardSynchronizer implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ClipboardSynchronizer");

  @NonNls private static final String DATA_TRANSFER_TIMEOUT_PROPERTY = "sun.awt.datatransfer.timeout";
  private static final String LONG_TIMEOUT = "2000";
  private static final String SHORT_TIMEOUT = "100";
  private static final FlavorTable FLAVOR_MAP = (FlavorTable)SystemFlavorMap.getDefaultFlavorMap();

  private volatile Transferable myCurrentContent = null;

  public static ClipboardSynchronizer getInstance() {
    return ApplicationManager.getApplication().getComponent(ClipboardSynchronizer.class);
  }

  @Override
  public void initComponent() {
    if (!Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) return;

    if (System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY) == null) {
      System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, LONG_TIMEOUT);
    }
  }

  @Override
  public void disposeComponent() {
    myCurrentContent = null;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ClipboardSynchronizer";
  }

  public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
    final Transferable currentContent = myCurrentContent;
    if (currentContent != null) {
      return currentContent.isDataFlavorSupported(dataFlavor);
    }

    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      if (SystemInfo.isLinux) {
        final Collection<DataFlavor> flavors = checkContentsQuick();
        if (flavors != null) {
          return flavors.contains(dataFlavor);
        }
      }

      final Transferable contents = getContents();
      return contents != null && contents.isDataFlavorSupported(dataFlavor);
    }
    return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(dataFlavor);
  }

  @Nullable
  public Transferable getContents() {
    try {
      return doGetContents();
    }
    catch (IllegalStateException e) {
      LOG.info(e);
      return null;
    }
  }

  private Transferable doGetContents() throws IllegalStateException {
    final Transferable currentContent = myCurrentContent;
    if (currentContent != null) {
      return currentContent;
    }

    if (SystemInfo.isMac && Registry.is("ide.mac.useNativeClipboard")) {
      final Transferable transferable = getContentsSafe();
      if (transferable != null) return transferable;
    }

    if (SystemInfo.isLinux) {
      final Collection<DataFlavor> flavors = checkContentsQuick();
      if (flavors != null && flavors.isEmpty()) {
        return null;
      }
    }

    IllegalStateException last = null;
    for (int i = 0; i < 3; i++) {
        try {
          return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(this);
        }
        catch (IllegalStateException e) {
          try {
            //noinspection BusyWait
            Thread.sleep(50);
          }
          catch (InterruptedException ignored) { }
          last = e;
        }
      }
    throw last;
  }

  public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
    for (int i = 0; i < 3; i++) {
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, owner);
        myCurrentContent = content;
      }
      catch (IllegalStateException e) {
        try {
          //noinspection BusyWait
          Thread.sleep(50);
        }
        catch (InterruptedException ignored) { }
        continue;
      }
      break;
    }
  }

  public void resetContent() {
    myCurrentContent = null;
  }

  public static Transferable getContentsSafe() {
    final Ref<Transferable> result = new Ref<Transferable>();
    Foundation.executeOnMainThread(new Runnable() {
      @Override
      public void run() {
        String plainText = "public.utf8-plain-text";
        String jvmObject = "application/x-java-jvm";

        ID pasteboard = Foundation.invoke("NSPasteboard", "generalPasteboard");
        ID types = Foundation.invoke(pasteboard, "types");
        IntegerType count = Foundation.invoke(types, "count");

        ID plainTextType = null;
        ID vmObjectType = null;

        for (int i = 0; i < count.intValue(); i++) {
          ID each = Foundation.invoke(types, "objectAtIndex:", i);
          String eachType = Foundation.toStringViaUTF8(each);
          if (plainText.equals(eachType)) {
            plainTextType = each;
          }

          if (eachType.contains(jvmObject)) {
            vmObjectType = each;
          }
        }

        if (vmObjectType != null && plainTextType != null) {
          ID text = Foundation.invoke(pasteboard, "stringForType:", plainTextType);
          result.set(new StringSelection(Foundation.toStringViaUTF8(text)));
        }

      }
    }, true, true);

    return result.get();
  }

  /**
   * Quickly checks availability of data in X11 clipboard selection.
   *
   * @return null if is unable to check; empty list if clipboard owner doesn't respond timely;
   * collection of available data flavors otherwise.
   */
  @Nullable
  public static Collection<DataFlavor> checkContentsQuick() {
    final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    final Class<? extends Clipboard> aClass = clipboard.getClass();
    if (!"sun.awt.X11.XClipboard".equals(aClass.getName())) return null;

    final Method getClipboardFormats;
    try {
      getClipboardFormats = aClass.getDeclaredMethod("getClipboardFormats");
      getClipboardFormats.setAccessible(true);
    }
    catch (Exception ignore) {
      return null;
    }

    final String timeout = System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY);
    System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, SHORT_TIMEOUT);

    try {
      final long[] formats = (long[])getClipboardFormats.invoke(clipboard);
      if (formats == null || formats.length == 0) {
        return Collections.emptySet();
      }
      else {
        //noinspection unchecked
        return DataTransferer.getInstance().getFlavorsForFormats(formats, FLAVOR_MAP).keySet();
      }
    }
    catch (IllegalAccessException ignore) { }
    catch (IllegalArgumentException ignore) { }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IllegalStateException) {
        throw (IllegalStateException)cause;
      }
    }
    finally {
      System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, timeout);
    }

    return null;
  }
}

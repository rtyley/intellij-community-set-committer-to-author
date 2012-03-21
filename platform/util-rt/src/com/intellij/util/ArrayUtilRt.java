/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.containers.ContainerUtilRt;

import java.io.File;
import java.util.Collection;

/**
 * Stripped-down version of {@code com.intellij.util.ArrayUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "SSBasedInspection"})
public class ArrayUtilRt {
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  public static final char[] EMPTY_CHAR_ARRAY = new char[0];
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final int[] EMPTY_INT_ARRAY = new int[0];
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  public static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  public static final Collection[] EMPTY_COLLECTION_ARRAY = new Collection[0];
  public static final File[] EMPTY_FILE_ARRAY = new File[0];
  public static final Runnable[] EMPTY_RUNNABLE_ARRAY = new Runnable[0];

  @NotNull
  public static String[] toStringArray(@NotNull Collection<String> collection) {
    if (collection.isEmpty()) return EMPTY_STRING_ARRAY;
    return ContainerUtilRt.toArray(collection, new String[collection.size()]);
  }
}

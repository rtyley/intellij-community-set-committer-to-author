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
package com.siyeh;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 30-Aug-2005
 */
@NonNls
public class HardcodedMethodConstants {
  public static final String CLONE = "clone";
  public static final String CLOSE = "close";
  public static final String COMPARE_TO = "compareTo";
  public static final String DUMP_STACKTRACE = "dumpStack";
  public static final String ENDS_WITH = "endsWith";
  public static final String EQUALS = "equals";
  public static final String EQUALS_IGNORE_CASE = "equalsIgnoreCase";
  public static final String ERR = "err";
  public static final String FINALIZE = "finalize";
  public static final String GC = "gc";
  public static final String GET = "get";
  public static final String GET_CHANNEL = "getChannel";
  public static final String GET_CLASS = "getClass";
  public static final String GET_CONNECTION = "getConnection";
  public static final String HASH_CODE = "hashCode";
  public static final String HAS_NEXT = "hasNext";
  public static final String INDEX_OF = "indexOf";
  public static final String JAVA_LANG = "java.lang";
  public static final String IS_INSTANCE = "isInstance";
  public static final String ITERATOR = "iterator";
  public static final String LAST_INDEX_OF = "lastIndexOf";
  public static final String LENGTH = "length";
  public static final String MAIN = "main";
  public static final String NEXT = "next";
  public static final String NOTIFY = "notify";
  public static final String NOTIFY_ALL = "notifyAll";
  public static final String OPEN = "open";
  public static final String OPEN_SESSION = "openSession";
  public static final String OUT = "out";
  public static final String PUT = "put";
  public static final String PUTALL = "putAll";
  public static final String PRINT_STACK_TRACE = "printStackTrace";
  public static final String REMOVE = "remove";
  public static final String RUN = "run";
  public static final String SERIAL_VERSION_UID = HighlightUtil.SERIAL_VERSION_UID_FIELD_NAME;
  public static final String SET = "set";
  public static final String SIZE = "size";
  public static final String STARTS_WITH = "startsWith";
  public static final String TO_LOWER_CASE = "toLowerCase";
  public static final String TO_UPPER_CASE = "toUpperCase";
  public static final String TO_STRING = "toString";
  public static final String WAIT = "wait";
}

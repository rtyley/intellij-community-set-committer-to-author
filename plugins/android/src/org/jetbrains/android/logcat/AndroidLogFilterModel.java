/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.intellij.diagnostic.logging.LogFilter;
import com.intellij.diagnostic.logging.LogFilterListener;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLogFilterModel extends LogFilterModel {
  private static final Pattern ANDROID_LOG_MESSAGE_PATTERN =
    Pattern.compile("\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+:\\s+[A-Z]+/(\\w+)\\(\\d+\\):.*");

  private final List<LogFilterListener> myListeners = new ArrayList<LogFilterListener>();

  private Log.LogLevel myPrevMessageLogLevel;
  
  private String myPrevTag;
  private LogFilter mySelectedLogFilter;
  private List<AndroidLogFilter> myLogFilters = new ArrayList<AndroidLogFilter>();

  public AndroidLogFilterModel(String initialLogLevelName) {
    for (Log.LogLevel logLevel : Log.LogLevel.values()) {
      AndroidLogFilter filter = new AndroidLogFilter(logLevel);
      if (logLevel.name().equals(initialLogLevelName)) {
        mySelectedLogFilter = filter;
      }
      myLogFilters.add(filter);
    }
  }

  public void updateCustomFilter(String filter) {
    super.updateCustomFilter(filter);
    setCustomFilter(filter);
    fireTextFilterChange();
  }

  public void updateTagFilter(String tag) {
    setTagFilter(tag);
    fireTextFilterChange();
  }

  protected abstract void setCustomFilter(String filter);

  protected void setTagFilter(String tag) {
  }

  protected String getTagFilter() {
    return "";
  }

  protected abstract void saveLogLevel(Log.LogLevel logLevel);

  public void addFilterListener(LogFilterListener listener) {
    myListeners.add(listener);
  }

  public void removeFilterListener(LogFilterListener listener) {
    myListeners.remove(listener);
  }

  private void fireTextFilterChange() {
    for (LogFilterListener listener : myListeners) {
      listener.onTextFilterChange();
    }
  }

  private void fireFilterChange(LogFilter filter) {
    for (LogFilterListener listener : myListeners) {
      listener.onFilterStateChange(filter);
    }
  }

  private static Key getProcessOutputType(@NotNull Log.LogLevel level) {
    switch (level) {
      case VERBOSE:
        return AndroidLogcatConstants.VERBOSE;
      case INFO:
        return AndroidLogcatConstants.INFO;
      case DEBUG:
        return AndroidLogcatConstants.DEBUG;
      case WARN:
        return AndroidLogcatConstants.WARNING;
      case ERROR:
        return AndroidLogcatConstants.ERROR;
      case ASSERT:
        return AndroidLogcatConstants.ASSERT;
    }
    return ProcessOutputTypes.STDOUT;
  }

  public boolean isApplicable(String text) {
    if (!super.isApplicable(text)) return false;
    
    if (!(mySelectedLogFilter == null || mySelectedLogFilter.isAcceptable(text))) {
      return false;
    }

    final String tagFilter = getTagFilter();
    if (tagFilter == null || tagFilter.length() == 0) {
      return true;
    }

    String tag = getLogTag(text);
    if (tag == null) {
      tag = myPrevTag;
    }

    return tagFilter.equals(tag);
  }

  public List<? extends LogFilter> getLogFilters() {
    return myLogFilters;
  }

  private class AndroidLogFilter extends LogFilter {
    final Log.LogLevel myLogLevel;

    private AndroidLogFilter(Log.LogLevel logLevel) {
      super(StringUtil.capitalize(logLevel.name().toLowerCase()));
      myLogLevel = logLevel;
    }

    @Override
    public boolean isAcceptable(String line) {
      Log.LogLevel logLevel = AndroidLogcatUtil.getLogLevel(line);
      if (logLevel == null) {
        logLevel = myPrevMessageLogLevel;
      }
      return logLevel != null && logLevel.getPriority() >= myLogLevel.getPriority();
    }
  }

  public boolean isFilterSelected(LogFilter filter) {
    return mySelectedLogFilter == filter;
  }

  public void selectFilter(LogFilter filter) {
    if (filter != mySelectedLogFilter && filter instanceof AndroidLogFilter) {
      mySelectedLogFilter = filter;
      saveLogLevel(((AndroidLogFilter)filter).myLogLevel);
      fireFilterChange(filter);
    }
  }

  public Key processLine(String line) {
    final String tag = getLogTag(line);
    
    if (tag != null) {
      myPrevTag = tag;
    }
    
    Log.LogLevel logLevel = AndroidLogcatUtil.getLogLevel(line);
    if (logLevel != null) {
      myPrevMessageLogLevel = logLevel;
    }
    return myPrevMessageLogLevel != null ? getProcessOutputType(myPrevMessageLogLevel) : ProcessOutputTypes.STDOUT;
  }

  @Nullable
  private static String getLogTag(String line) {
    String prevTag = null;
    final Matcher matcher = ANDROID_LOG_MESSAGE_PATTERN.matcher(line);
    if (matcher.matches()) {
      final String tag = matcher.group(1).trim();
      if (tag.length() > 0) {
        prevTag = tag;
      }
    }
    return prevTag;
  }
}

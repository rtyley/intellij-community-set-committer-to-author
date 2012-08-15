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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Contains various utility methods to be used during showing arrangement settings.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 9:14 AM
 */
public class ArrangementConfigUtil {

  private ArrangementConfigUtil() {
  }

  /**
   * Allows to answer what new settings are available for a particular {@link ArrangementMatcherSettings arrangement matcher rules}.
   *
   * @param filter    filter to use
   * @param settings  object that encapsulates information about current arrangement matcher settings
   * @return          map which contains information on what new new settings are available at the current situation
   */
  @NotNull
  public static Map<ArrangementSettingType, Collection<?>> buildAvailableOptions(@NotNull ArrangementStandardSettingsAware filter,
                                                                                 @Nullable ArrangementSettingsNode settings)
  {
    Map<ArrangementSettingType, Collection<?>> result = new EnumMap<ArrangementSettingType, Collection<?>>(ArrangementSettingType.class);
    processData(filter, settings, result, ArrangementSettingType.TYPE, ArrangementEntryType.values());
    processData(filter, settings, result, ArrangementSettingType.MODIFIER, ArrangementModifier.values());
    return result;
  }

  private static <T> void processData(@NotNull ArrangementStandardSettingsAware filter,
                                      @Nullable ArrangementSettingsNode settings,
                                      @NotNull Map<ArrangementSettingType, Collection<?>> result,
                                      @NotNull ArrangementSettingType type,
                                      @NotNull T[] values)
  {
    List<T> data = null;
    for (T v : values) {
      if (!isEnabled(v, filter, settings)) {
        continue;
      }
      if (data == null) {
        data = new ArrayList<T>();
      }
      data.add(v);
    }
    if (data != null) {
      result.put(type, data);
    }
  }

  public static boolean isEnabled(@NotNull Object conditionId,
                                  @NotNull ArrangementStandardSettingsAware filter,
                                  @Nullable ArrangementSettingsNode settings)
  {
    if (conditionId instanceof ArrangementEntryType) {
      return filter.isEnabled((ArrangementEntryType)conditionId, settings);
    }
    else if (conditionId instanceof ArrangementModifier) {
      return filter.isEnabled((ArrangementModifier)conditionId, settings);
    }
    else {
      return false;
    }
  }
  
  @Nullable
  public static Point getLocationOnScreen(@NotNull JComponent component) {
    int dx = 0;
    int dy = 0;
    for (Container c = component; c != null; c = c.getParent()) {
      if (c.isShowing()) {
        Point locationOnScreen = c.getLocationOnScreen();
        locationOnScreen.translate(dx, dy);
        return locationOnScreen;
      }
      else {
        Point location = c.getLocation();
        dx += location.x;
        dy += location.y;
      }
    }
    return null;
  }
}

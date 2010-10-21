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
package com.intellij.notification;

/**
 * @author spleaner
 */
public enum NotificationDisplayType {

  NONE("Ignore"),
  BALLOON("Balloon"),
  STICKY_BALLOON("Sticky balloon"),
  BALLOON_ONLY("Balloon only");

  private final String myTitle;

  private NotificationDisplayType(final String humanTitle) {
    myTitle = humanTitle;
  }

  public String getTitle() {
    return myTitle;
  }

}

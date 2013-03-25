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
package com.intellij.ide.fileTemplates.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/6/11
 */
public final class BundledFileTemplate extends FileTemplateBase {
  
  private final DefaultTemplate myDefaultTemplate;
  private final boolean myInternal;
  private boolean myEnabled = true; // when user 'deletes' bundled plugin, it simply becomes disabled

  public BundledFileTemplate(@NotNull DefaultTemplate defaultTemplate, boolean internal) {
    myDefaultTemplate = defaultTemplate;
    myInternal = internal;
  }
  
  @NotNull
  public String getName() {
    return myDefaultTemplate.getName();
  }
  
  @NotNull
  public String getExtension() {
    return myDefaultTemplate.getExtension();
  }

  public void setName(@NotNull String name) {
    // empty, cannot change name for bundled template
  }

  public void setExtension(@NotNull String extension) {
    // empty, cannot change extension for bundled template
  }

  @NotNull
  protected String getDefaultText() {
    return myDefaultTemplate.getText();
  }

  @NotNull
  public final String getDescription() {
    return myDefaultTemplate.getDescriptionText();
  }

  public boolean isDefault() {
    // todo: consider isReformat option here?
    if (!getText().equals(getDefaultText())) {
      return false;
    }
    return true;
  }

  @Override
  public BundledFileTemplate clone() {
    return (BundledFileTemplate)super.clone();
  }

  public boolean isEnabled() {
    return myInternal || myEnabled;
  }

  public void setEnabled(boolean enabled) {
    if (enabled != myEnabled) {
      myEnabled = enabled;
      if (!enabled) {
        revertToDefaults();
      }
    }
  }

  public void revertToDefaults() {
    setText(null);
    setReformatCode(DEFAULT_REFORMAT_CODE_VALUE);
  }
  
  public boolean isTextModified() {
    return !getText().equals(getDefaultText());
  }
}

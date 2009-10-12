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
package git4idea.actions;

import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Rebase abort action
 */
public class GitRebaseSkip extends GitAbstractRebaseResumeAction {

  /**
   * {@inheritDoc}
   */
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("rebase.skip.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @NonNls
  protected String getOptionName() {
    return "--skip";
  }

  /**
   * {@inheritDoc}
   */
  protected String getActionTitle() {
    return GitBundle.getString("rebase.skip.action.name");
  }
}
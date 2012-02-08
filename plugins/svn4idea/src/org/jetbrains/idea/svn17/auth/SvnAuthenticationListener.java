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
package org.jetbrains.idea.svn17.auth;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import java.util.EventListener;

public interface SvnAuthenticationListener extends EventListener {
  void requested(final ProviderType type, final SVNURL url, String realm, String kind, boolean canceled);
  void actualSaveWillBeTried(final ProviderType type, final SVNURL url, String realm, String kind);
  void saveAttemptStarted(final ProviderType type, final SVNURL url, String realm, String kind);
  void saveAttemptFinished(final ProviderType type, final SVNURL url, String realm, String kind);
  void acknowledge(boolean accepted, String kind, String realm, SVNErrorMessage message, SVNAuthentication authentication);
}

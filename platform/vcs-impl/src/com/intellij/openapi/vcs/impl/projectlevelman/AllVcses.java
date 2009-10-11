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
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.VcsEP;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AllVcses implements AllVcsesI, Disposable {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.projectlevelman.AllVcses");
  private final Map<String, AbstractVcs> myVcses;

  private final Object myLock;
  private final Project myProject;
  private final Map<String, VcsEP> myExtensions;

  private AllVcses(final Project project) {
    myProject = project;
    myVcses = new HashMap<String, AbstractVcs>();
    myLock = new Object();

    final VcsEP[] vcsEPs = Extensions.getExtensions(VcsEP.EP_NAME, myProject);
    myExtensions = new HashMap<String, VcsEP>();
    for (VcsEP vcsEP : vcsEPs) {
      myExtensions.put(vcsEP.name, vcsEP);
    }

    for (VcsEP ep : myExtensions.values()) {
      addVcs(ep.getVcs(myProject));
    }
  }

  public static AllVcsesI getInstance(final Project project) {
    return ServiceManager.getService(project, AllVcsesI.class);
  }

  private void addVcs(final AbstractVcs vcs) {
    registerVcs(vcs);
    myVcses.put(vcs.getName(), vcs);
  }

  /*@Nullable
  private AbstractVcs lazyGet(final String name) {
    final AbstractVcs vcs = myVcses.get(name);
    if (vcs != null) {
      return vcs;
    }
    final VcsEP ep = myExtensions.get(name);
    if (ep != null) {
      final AbstractVcs vcs1 = ep.getVcs(myProject);
      addVcs(vcs1);
      return vcs1;
    }
    return null;
  }*/

  private void registerVcs(final AbstractVcs vcs) {
    try {
      vcs.loadSettings();
      vcs.doStart();
    }
    catch (VcsException e) {
      LOG.debug(e);
    }
    vcs.getProvidedStatuses();
  }

  public void registerManually(@NotNull final AbstractVcs vcs) {
    synchronized (myLock) {
      if (myVcses.containsKey(vcs.getName())) return;
      addVcs(vcs);
    }
  }

  public void unregisterManually(@NotNull final AbstractVcs vcs) {
    synchronized (myLock) {
      if (! myVcses.containsKey(vcs.getName())) return;
      unregisterVcs(vcs);
      myVcses.remove(vcs.getName());
    }
  }

  public AbstractVcs getByName(final String name) {
    synchronized (myLock) {
      //return lazyGet(name);
      return myVcses.get(name);
    }
  }

  /*public AbstractVcs[] getList() {
    return myList;
  }*/

  public void dispose() {
    synchronized (myLock) {
      for (AbstractVcs vcs : myVcses.values()) {
        unregisterVcs(vcs);
      }
    }
  }

  private void unregisterVcs(AbstractVcs vcs) {
    try {
      vcs.doShutdown();
    }
    catch (VcsException e) {
      LOG.info(e);
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myVcses.isEmpty();
    }
  }

  public AbstractVcs[] getAll() {
    synchronized (myLock) {
      final AbstractVcs[] vcses = myVcses.values().toArray(new AbstractVcs[myVcses.size()]);
      Arrays.sort(vcses, new Comparator<AbstractVcs>() {
          public int compare(final AbstractVcs o1, final AbstractVcs o2) {
            return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
          }
        });
      return vcses;
    }
  }
}

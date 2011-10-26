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
package com.intellij.internal;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ResourceUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

/**
 * @author stathik
 * Date: Nov 6, 2003
 */
public class DumpInspectionDescriptionsAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.DumpInspectionDescriptionsAction");

  public DumpInspectionDescriptionsAction() {
    super("Dump inspection descriptions");
  }

  @Override
  public void actionPerformed(final AnActionEvent event) {
    final InspectionProfile profile = (InspectionProfile)InspectionProfileManager.getInstance().getRootProfile();
    final InspectionProfileEntry[] tools = profile.getInspectionTools(null);

    final Collection<String> classes = Sets.newTreeSet();
    final Map<String, Collection<String>> groups = Maps.newTreeMap();

    final String tempDirectory = FileUtil.getTempDirectory();
    final File descDirectory = new File(tempDirectory, "inspections");
    if (!descDirectory.mkdirs() && !descDirectory.isDirectory()) {
      LOG.error("Unable to create directory: " + descDirectory.getAbsolutePath());
      return;
    }

    for (InspectionProfileEntry tool : tools) {
      classes.add(getInspectionClass(tool).getName());

      final String group = getGroupName(tool);
      Collection<String> names = groups.get(group);
      if (names == null) groups.put(group, (names = Sets.newTreeSet()));
      names.add(tool.getShortName());

      final URL url = getDescriptionUrl(tool);
      if (url != null) {
        doDump(new File(descDirectory, tool.getShortName() + ".html"), new Processor() {
          @Override public void process(BufferedWriter writer) throws Exception {
            writer.write(ResourceUtil.loadText(url));
          }
        });
      }
    }
    doNotify("Inspection descriptions dumped to\n" + descDirectory.getAbsolutePath());

    final File fqnListFile = new File(tempDirectory, "inspection_fqn_list.txt");
    final boolean fqnOk = doDump(fqnListFile, new Processor() {
      @Override public void process(BufferedWriter writer) throws Exception {
        for (String name : classes) {
          writer.write(name);
          writer.newLine();
        }
      }
    });
    if (fqnOk) {
      doNotify("Inspection class names dumped to\n" + fqnListFile.getAbsolutePath());
    }

    final File groupsFile = new File(tempDirectory, "inspection_groups.txt");
    final boolean groupsOk = doDump(groupsFile, new Processor() {
      @Override public void process(BufferedWriter writer) throws Exception {
        for (Map.Entry<String, Collection<String>> entry : groups.entrySet()) {
          writer.write(entry.getKey());
          writer.write(':');
          writer.newLine();
          for (String name : entry.getValue()) {
            writer.write("  ");
            writer.write(name);
            writer.newLine();
          }
        }
      }
    });
    if (groupsOk) {
      doNotify("Inspection groups dumped to\n" + fqnListFile.getAbsolutePath());
    }
  }

  private static Class getInspectionClass(final InspectionProfileEntry tool) {
    return tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getTool().getClass() : tool.getClass();
  }

  private static String getGroupName(final InspectionProfileEntry tool) {
    final String name = tool.getGroupDisplayName();
    return StringUtil.isEmptyOrSpaces(name) ? "General" : name;
  }

  private static URL getDescriptionUrl(final InspectionProfileEntry tool) {
    final Class aClass = getInspectionClass(tool);
    return ResourceUtil.getResource(aClass, "/inspectionDescriptions", tool.getShortName() + ".html");
  }

  private interface Processor {
    void process(BufferedWriter writer) throws Exception;
  }

  private static boolean doDump(final File file, final Processor processor) {
    try {
      final BufferedWriter writer = new BufferedWriter(new FileWriter(file));
      try {
        processor.process(writer);
        return true;
      }
      finally {
        writer.close();
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
  }

  private static void doNotify(final String message) {
    Notifications.Bus.notify(new Notification("Actions", "Inspection descriptions dumped", message, NotificationType.INFORMATION));
  }
}
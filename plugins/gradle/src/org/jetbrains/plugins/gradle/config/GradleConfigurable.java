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
package org.jetbrains.plugins.gradle.config;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleIcons;
import org.jetbrains.plugins.gradle.util.GradleLibraryManager;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public class GradleConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  @NonNls public static final String HELP_TOPIC = "reference.settingsdialog.project.gradle";

  /**
   * There is a possible case that end-user defines gradle home while particular project is open. We want to use that gradle
   * home for the default project as well until that is manually changed for the default project.
   * <p/>
   * Current constant holds key of the value that defines if gradle home for default project should be tracked from
   * the non-default one.
   * <p/>
   * This property has a form of 'not-propagate' in order to default to 'propagate'.
   */
  @NonNls private static final String NOT_PROPAGATE_GRADLE_HOME_TO_DEFAULT_PROJECT = "gradle.not.propagate.home.to.default.project";

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final GradleLibraryManager myLibraryManager;
  private final Project              myProject;

  private GradleHomeSettingType myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;

  private JComponent         myComponent;
  private NamePathComponent  myGradleHomeComponent;
  private boolean            myPathManuallyModified;
  private boolean            myShowBalloonIfNecessary;

  public GradleConfigurable(@Nullable Project project) {
    this(project, ServiceManager.getService(GradleLibraryManager.class));
  }

  public GradleConfigurable(@Nullable Project project, @NotNull GradleLibraryManager gradleLibraryManager) {
    myLibraryManager = gradleLibraryManager;
    myProject = project;
    doCreateComponent();
  }

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      doCreateComponent();
    }
    return myComponent;
  }

  private void doCreateComponent() {
    myComponent = new JPanel(new GridBagLayout()) {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        if (!myShowBalloonIfNecessary) {
          return;
        }
        myShowBalloonIfNecessary = false;
        MessageType messageType = null;
        switch (myGradleHomeSettingType) {
          case DEDUCED: messageType = MessageType.INFO; break;
          case EXPLICIT_INCORRECT:
          case UNKNOWN: messageType = MessageType.ERROR; break;
          default:
        }
        if (messageType != null) {
          new DelayedBalloonInfo(messageType, myGradleHomeSettingType).run();
        }
      }
    };
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;

    myGradleHomeComponent = new NamePathComponent(
      "", GradleBundle.message("gradle.import.text.home.path"), GradleBundle.message("gradle.import.text.home.path"), "",
      false,
      false
    );
    myGradleHomeComponent.getPathComponent().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        useNormalColorForPath();
        myPathManuallyModified = true;
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        useNormalColorForPath();
        myPathManuallyModified = true;
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
      }
    });
    myGradleHomeComponent.setNameComponentVisible(false);
    myComponent.add(myGradleHomeComponent, constraints);
    myComponent.add(Box.createVerticalGlue());
  }

  @Override
  public boolean isModified() {
    myShowBalloonIfNecessary = true;
    if (!myPathManuallyModified) {
      return false;
    }
    String newPath = myGradleHomeComponent.getPath();
    String oldPath = GradleSettings.getInstance(myProject).GRADLE_HOME;
    boolean modified = newPath == null ? oldPath == null : !newPath.equals(oldPath);
    if (modified) {
      useNormalColorForPath();
    }
    return modified;
  }

  @Override
  public void apply() {
    useNormalColorForPath();
    String path = myGradleHomeComponent.getPath();
    GradleSettings.getInstance(myProject).GRADLE_HOME = path;
    
    // There is a possible case that user defines gradle home for particular open project. We want to apply that value
    // to the default project as well if it's still non-defined.
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    if (defaultProject == myProject) {
      PropertiesComponent.getInstance().setValue(NOT_PROPAGATE_GRADLE_HOME_TO_DEFAULT_PROJECT, Boolean.TRUE.toString());
      return;
    }
    
    
    if (!StringUtil.isEmpty(path)
        && !Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(NOT_PROPAGATE_GRADLE_HOME_TO_DEFAULT_PROJECT)))
    {
      GradleSettings.getInstance(defaultProject).GRADLE_HOME = path;
    } 
  }

  @Override
  public void reset() {
    useNormalColorForPath();
    myPathManuallyModified = false;
    String valueToUse = GradleSettings.getInstance(myProject).GRADLE_HOME;
    if (StringUtil.isEmpty(valueToUse)) {
      valueToUse = GradleSettings.getInstance(ProjectManager.getInstance().getDefaultProject()).GRADLE_HOME;
    } 
    if (!StringUtil.isEmpty(valueToUse)) {
      myGradleHomeSettingType = myLibraryManager.isGradleSdkHome(new File(valueToUse)) ?
                                GradleHomeSettingType.EXPLICIT_CORRECT :
                                GradleHomeSettingType.EXPLICIT_INCORRECT;
      if (myGradleHomeSettingType == GradleHomeSettingType.EXPLICIT_INCORRECT) {
        new DelayedBalloonInfo(MessageType.ERROR, myGradleHomeSettingType).run();
      }
      else {
        myAlarm.cancelAllRequests();
      }
      myGradleHomeComponent.setPath(valueToUse);
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.UNKNOWN;
    deduceGradleHomeIfPossible();
  }

  private void useNormalColorForPath() {
    myGradleHomeComponent.getPathComponent().setForeground(UIManager.getColor("TextField.foreground"));
  }

  /**
   * Updates GUI of the gradle configurable in order to show deduced path to gradle (if possible).
   */
  private void deduceGradleHomeIfPossible() {
    File gradleHome = myLibraryManager.getGradleHome(myProject);
    if (gradleHome == null) {
      new DelayedBalloonInfo(MessageType.WARNING, GradleHomeSettingType.UNKNOWN).run();
      return;
    }
    myGradleHomeSettingType = GradleHomeSettingType.DEDUCED;
    new DelayedBalloonInfo(MessageType.INFO, GradleHomeSettingType.DEDUCED).run();
    if (myGradleHomeComponent != null) {
      myGradleHomeComponent.setPath(gradleHome.getPath());
      myGradleHomeComponent.getPathComponent().setForeground(UIManager.getColor("TextField.inactiveForeground"));
      myPathManuallyModified = false;
    }
  }

  @Override
  public void disposeUIResources() {
    myComponent = null;
    myGradleHomeComponent = null;
    myPathManuallyModified = false;
  }

  public Icon getIcon() {
    return GradleIcons.GRADLE_ICON;
  }

  @NotNull
  public String getHelpTopic() {
    return HELP_TOPIC;
  }

  /**
   * @return UI component that manages path to the local gradle distribution to use
   */
  @NotNull
  public NamePathComponent getGradleHomeComponent() {
    if (myGradleHomeComponent == null) {
      createComponent();
    }
    return myGradleHomeComponent;
  }

  @NotNull
  public GradleHomeSettingType getCurrentGradleHomeSettingType() {
    String path = myGradleHomeComponent.getPath();
    if (path == null || StringUtil.isEmpty(path.trim())) {
      return GradleHomeSettingType.UNKNOWN;
    }
    if (isModified()) {
      return myLibraryManager.isGradleSdkHome(new File(path)) ? GradleHomeSettingType.EXPLICIT_CORRECT
                                                              : GradleHomeSettingType.EXPLICIT_INCORRECT;
    }
    return myGradleHomeSettingType;
  }

  private class DelayedBalloonInfo implements Runnable {
    private final MessageType myMessageType;
    private final String      myText;

    DelayedBalloonInfo(@NotNull MessageType messageType, @NotNull GradleHomeSettingType settingType) {
      myMessageType = messageType;
      myText = settingType.getDescription();
    }

    @Override
    public void run() {
      if (myGradleHomeComponent == null || !myGradleHomeComponent.getPathComponent().isShowing()) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(this, (int)TimeUnit.MILLISECONDS.toMillis(200));
        return;
      }
      GradleUtil.showBalloon(myGradleHomeComponent.getPathComponent(), myMessageType, myText);
    }
  }
}
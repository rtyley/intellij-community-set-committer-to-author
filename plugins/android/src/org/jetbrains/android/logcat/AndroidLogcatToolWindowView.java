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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.intellij.CommonBundle;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFilterModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.actions.AndroidEnableDdmsAction;
import org.jetbrains.android.ddms.AdbManager;
import org.jetbrains.android.ddms.AdbNotRespondingException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLogcatToolWindowView implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.AndroidLogcatToolWindowView");

  private final Project myProject;
  private JComboBox myDeviceCombo;
  private JPanel myConsoleWrapper;
  private JPanel myPanel;
  private JButton myClearLogButton;
  private JPanel mySearchComponentWrapper;
  private volatile IDevice myDevice;
  private final Object myLock = new Object();
  private final LogConsoleBase myLogConsole;
  private volatile Reader myCurrentReader;

  private final AndroidDebugBridge.IDeviceChangeListener myDeviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
    public void deviceConnected(IDevice device) {
      updateInUIThread();
    }

    public void deviceDisconnected(IDevice device) {
      updateInUIThread();
    }

    public void deviceChanged(IDevice device, int changeMask) {
      if ((changeMask & IDevice.CHANGE_STATE) != 0) {
        if (device == myDevice) {
          myDevice = null;
          updateInUIThread();
        }
      }
    }
  };

  private void updateInUIThread() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        updateDevices();
        updateLogConsole();
      }
    });
  }

  private class MyLoggingReader extends AndroidLoggingReader {
    @NotNull
    protected Object getLock() {
      return myLock;
    }

    protected Reader getReader() {
      return myCurrentReader;
    }
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  public AndroidLogcatToolWindowView(final Project project) {
    myProject = project;
    Disposer.register(myProject, this);

    myDeviceCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateLogConsole();
      }
    });
    myDeviceCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value == null) {
          setText("<html><font color='red'>[none]</font></html>");
        }
        return this;
      }
    });
    LogFilterModel logFilterModel = new AndroidLogFilterModel(AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL) {
      @Override
      protected void setCustomFilter(String filter) {
        AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER = filter;
      }

      @Override
      protected void saveLogLevel(Log.LogLevel logLevel) {
        AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL = logLevel.name();
      }

      @Override
      public String getCustomFilter() {
        return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER;
      }
    };
    myLogConsole = new LogConsoleBase(project, new MyLoggingReader() {
    }, null, false, logFilterModel) {
      @Override
      public boolean isActive() {
        return AndroidLogcatToolWindowView.this.isActive();
      }
    };
    mySearchComponentWrapper.add(myLogConsole.getSearchComponent());
    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(myLogConsole.getToolbarActions());
    group.add(new AndroidEnableDdmsAction(AndroidUtils.DDMS_ICON));
    final JComponent tbComp =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent();
    myConsoleWrapper.add(tbComp, BorderLayout.WEST);
    myConsoleWrapper.add(myLogConsole.getComponent(), BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);
    myClearLogButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
        if (device != null) {
          AndroidLogcatUtil.clearLogcat(project, device);
          myLogConsole.clear();
        }
      }
    });
    try {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener);
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      Messages.showErrorDialog(e.getMessage(), CommonBundle.getErrorTitle());
    }
    updateDevices();
    updateLogConsole();
  }

  protected abstract boolean isActive();

  public void activate() {
    updateDevices();
    updateLogConsole();
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = (IDevice)myDeviceCombo.getSelectedItem();
    if (myDevice != device) {
      synchronized (myLock) {
        myDevice = device;
        if (myCurrentReader != null) {
          try {
            myCurrentReader.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        if (device != null) {
          myCurrentReader = AndroidLogcatUtil.startLoggingThread(myProject, device, false, myLogConsole);
        }
      }
    }
  }

  @Nullable
  private static AndroidPlatform getAndroidPlatform(@NotNull Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      if (platform != null) {
        return platform;
      }
    }
    return null;
  }

  private void updateDevices() {
    AndroidPlatform platform = getAndroidPlatform(myProject);
    if (platform != null) {
      final AndroidDebugBridge debugBridge = platform.getSdk().getDebugBridge(myProject);
      if (debugBridge != null) {
        IDevice[] devices;
        try {
          devices = AdbManager.compute(new Computable<IDevice[]>() {
            public IDevice[] compute() {
              return debugBridge.getDevices();
            }
          }, true);
        }
        catch (AdbNotRespondingException e) {
          Messages.showErrorDialog(myProject, e.getMessage(), CommonBundle.getErrorTitle());
          return;
        }
        Object temp = myDeviceCombo.getSelectedItem();
        myDeviceCombo.setModel(new DefaultComboBoxModel(devices));
        if (devices.length > 0 && temp == null) {
          temp = devices[0];
        }
        myDeviceCombo.setSelectedItem(temp);
      }
    }
    else {
      myDeviceCombo.setModel(new DefaultComboBoxModel());
    }
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  public void dispose() {
    try {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener);
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      Messages.showErrorDialog(myProject, e.getMessage(), CommonBundle.getErrorTitle());
    }
  }

}

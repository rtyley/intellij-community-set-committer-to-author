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
package com.intellij.cvsSupport2.connections.ext;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorkerImpl;
import com.intellij.cvsSupport2.connections.ssh.SSHPasswordProviderImpl;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionUtil;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class ExtConnectionCvsSettings extends CvsConnectionSettings {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ext.ExtConnectionCvsSettings");
  @NonNls private static final String UNHANDLED_RESPONSE_PREFIX = "Unhandled response: ";
  private final SshSettings mySshSettings;

  public ExtConnectionCvsSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
    mySshSettings = cvsRootConfiguration.SSH_FOR_EXT_CONFIGURATION;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {
    return CvsConnectionUtil.createExtConnection(this, getExtConfiguration(), mySshSettings,
                                                 SSHPasswordProviderImpl.getInstance(),
                                                 cvsRootConfiguration.PROXY_SETTINGS,
                                                 errorRegistry,
                                                 CvsApplicationLevelConfiguration.getInstance().TIMEOUT * 1000);
  }

  public int getDefaultPort() {
    return 22;
  }

  public CvsLoginWorker getLoginWorker(ModalityContext executor, Project project) {
    return new ExtLoginWorker(project, this, executor);
  }

  private class ExtLoginWorker extends CvsLoginWorkerImpl<ExtConnectionCvsSettings> {

    public ExtLoginWorker(final Project project, final ExtConnectionCvsSettings settings, final ModalityContext executor) {
      super(project, settings, executor);
    }

    @Override
    protected void silentLoginImpl(boolean forceCheck) throws AuthenticationException {
      IConnection connection = mySettings.createConnection(new ReadWriteStatistics());
      try {
        connection.open(new StreamLogger());
        mySettings.setOffline(false);
      }
      finally {
        try {
          connection.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }

    @Override
    public boolean promptForPassword() {
      return SshConnectionUtil.promptForPassword(mySshSettings, getCvsRootAsString());
    }

    @Override
    protected void clearOldCredentials() {
      if (!getExtConfiguration().USE_INTERNAL_SSH_IMPLEMENTATION) {
        return;
      }
      if (mySshSettings.USE_PPK) {
        SSHPasswordProviderImpl.getInstance().removePPKPasswordFor(getCvsRootAsString());
      } else {
        SSHPasswordProviderImpl.getInstance().removePasswordFor(getCvsRootAsString());
      }
    }
  }

  public CommandException processException(CommandException t) {
    Exception sourceException = t.getUnderlyingException();
    if (!(sourceException instanceof IOException)) return t;
    String localizedMessage = t.getLocalizedMessage();
    if (!localizedMessage.startsWith(UNHANDLED_RESPONSE_PREFIX)) return t;
    String response = localizedMessage.substring(UNHANDLED_RESPONSE_PREFIX.length(),
                                                 localizedMessage.length() - 1);
    if (StringUtil.startsWithConcatenationOf(response, USER + "@", HOST)) {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.ext.server.rejected.access")));
    }
    else {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.cannot.establish.external.connection", response)));
    }
  }
}

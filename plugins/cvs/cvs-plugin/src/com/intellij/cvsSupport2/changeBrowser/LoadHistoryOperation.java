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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsExecutionEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.LocalPathIndifferentOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.RlogCommand;
import com.intellij.util.Consumer;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.log.LogInformation;

import java.text.SimpleDateFormat;
import java.util.*;

public class LoadHistoryOperation extends LocalPathIndifferentOperation {

  @NonNls private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.US));
  private static final Collection<String> ourDoNotSupportingSOptionServers = new HashSet<String>();

  private final String myModule;
  private final Date myDateFrom;
  private final Date myDateTo;
  private final Consumer<LogInformationWrapper> myConsumer;
  private final String[] myRevisions;
  private final boolean myNoTags;

  public LoadHistoryOperation(CvsEnvironment environment, String module,
                              @Nullable Date dateFrom,
                              @Nullable Date dateTo,
                              @NotNull final Consumer<LogInformationWrapper> consumer) {
    this(environment, consumer, module, dateFrom, dateTo, false);
  }

  public LoadHistoryOperation(CvsEnvironment environment, Consumer<LogInformationWrapper> consumer, String module, String... revisions) {
    this(environment, consumer, module, null, null, true, revisions);
  }

  private LoadHistoryOperation(CvsEnvironment environment,
                              @NotNull final Consumer<LogInformationWrapper> consumer,
                              String module,
                              @Nullable Date dateFrom,
                              @Nullable Date dateTo,
                              boolean noTags,
                              @NotNull String... revisions) {
    super(environment);
    myConsumer = consumer;
    myModule = module;
    myDateFrom = dateFrom;
    myDateTo = dateTo;
    myNoTags = noTags;
    myRevisions = revisions;
  }

  protected Command createCommand(CvsRootProvider root, CvsExecutionEnvironment cvsExecutionEnvironment) {
    final RlogCommand command = new RlogCommand();
    command.setModuleName(myModule);
    command.setHeadersOnly(false);
    command.setNoTags(myNoTags);
    if (myDateFrom != null) {
      command.setDateFrom(DATE_FORMAT.format(myDateFrom));
    }
    if (myDateTo != null) {
      command.setDateTo(DATE_FORMAT.format(myDateTo));
    }
    command.setRevisions(myRevisions);

    if (ourDoNotSupportingSOptionServers.contains(root.getCvsRootAsString())) {
      command.setSuppressEmptyHeaders(false);
    }

    return command;
  }

  public void disableSuppressEmptyHeadersForCurrentCvsRoot() {
    ourDoNotSupportingSOptionServers.add(myEnvironment.getCvsRootAsString());
  }

  @NonNls
  protected String getOperationName() {
    return "rlog";
  }

  public void fileInfoGenerated(Object info) {
    super.fileInfoGenerated(info);
    if (!(info instanceof LogInformation)) {
      return;
    }
    final LogInformation logInfo = (LogInformation)info;
    final LogInformationWrapper wrapper = LogInformationWrapper.wrap(myEnvironment.getRepository(), logInfo);
    if (wrapper == null) {
      return;
    }
    myConsumer.consume(wrapper);
  }

  public boolean runInReadThread() {
    return false;
  }

  protected boolean runInExclusiveLock() {
    return false;
  }
}

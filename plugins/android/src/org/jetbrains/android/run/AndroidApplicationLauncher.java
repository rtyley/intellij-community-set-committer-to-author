/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.run;

import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 28, 2009
 * Time: 1:40:49 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AndroidApplicationLauncher {
  public abstract boolean launch(@NotNull AndroidRunningState state, @NotNull IDevice device);

  public boolean isReadyForDebugging(ClientData data) {
    return data.getDebuggerConnectionStatus() == ClientData.DebuggerStatus.WAITING;
  }
}

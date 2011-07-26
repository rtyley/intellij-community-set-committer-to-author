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
package git4idea.remote;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
@State(
  name = "GitRememberedInputs",
  storages = @Storage( file = "$APP_CONFIG$/vcs.xml")
)
public class GitRememberedInputs implements PersistentStateComponent<GitRememberedInputs.State> {

  private State myState = new State();

  public static GitRememberedInputs getInstance() {
    return ServiceManager.getService(GitRememberedInputs.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public void addUrl(String url) {
    myState.myVisitedUrls.add(url);
  }

  public List<String> getVisitedUrls() {
    return myState.myVisitedUrls;
  }

  public String getCloneParentDir() {
    return myState.myCloneParentDir;
  }

  public void setCloneParentDir(String cloneParentDir) {
    myState.myCloneParentDir = cloneParentDir;
  }

  public static class State {
    public List<String> myVisitedUrls = new ArrayList<String>();
    public String myCloneParentDir = "";
  }
}

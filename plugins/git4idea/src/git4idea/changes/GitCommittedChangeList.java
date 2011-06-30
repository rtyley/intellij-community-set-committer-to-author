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
package git4idea.changes;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;

import java.util.Collection;
import java.util.Date;

/**
 * @author irengrig
 *         Date: 6/30/11
 *         Time: 4:03 PM
 */
public class GitCommittedChangeList extends CommittedChangeListImpl {
  private final String myFullHash;

  public GitCommittedChangeList(String name,
                                String comment,
                                String committerName,
                                long number,
                                Date commitDate, Collection<Change> changes, String fullHash) {
    super(name, comment, committerName, number, commitDate, changes);
    myFullHash = fullHash;
  }

  public String getFullHash() {
    return myFullHash;
  }
}

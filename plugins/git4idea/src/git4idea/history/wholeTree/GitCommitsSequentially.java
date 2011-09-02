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
package git4idea.history.wholeTree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ThrowableIterator;
import com.intellij.util.continuation.ContinuationContext;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/30/11
 * Time: 7:21 PM
 */
public interface GitCommitsSequentially {
  // final -1 if from start
  // this method also can be used to check whether history starts from start
  void iterateDescending(VirtualFile file, final long commitTime, final Processor<Pair<AbstractHash, Long>> consumer) throws VcsException;
  void pushUpdate(final Project project, final VirtualFile file, ContinuationContext context);
}

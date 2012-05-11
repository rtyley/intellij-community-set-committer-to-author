/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.TestClientRunner;

import java.io.File;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/2/12
 * Time: 5:33 PM
 */
public class SvnClientRunnerImpl implements SvnClientRunner {
  private final TestClientRunner myTestClientRunner;

  public SvnClientRunnerImpl(final TestClientRunner testClientRunner) {
    myTestClientRunner = testClientRunner;
  }

  @Override
  public ProcessOutput runSvn(final VirtualFile file, String... commandLine) throws IOException {
    return myTestClientRunner.runClient("svn", null, new File(file.getPath()), commandLine);
    }

  @Override
  public void checkin(final VirtualFile file) throws IOException {
    AbstractVcsTestCase.verify(runSvn(file, "ci", "-m", "test"));
  }

  @Override
  public void update(final VirtualFile file) throws IOException {
    AbstractVcsTestCase.verify(runSvn(file, "up", "--accept", "postpone"));
  }

  @Override
  public void checkout(final String repoUrl, final VirtualFile file) throws IOException {
    AbstractVcsTestCase.verify(runSvn(file, "co", repoUrl, "."));
  }

  @Override
  public void add(VirtualFile root, String path) throws IOException {
    AbstractVcsTestCase.verify(runSvn(root, "add", path));
  }

  @Override
  public void delete(VirtualFile root, String path) throws IOException {
    AbstractVcsTestCase.verify(runSvn(root, "delete", path));
  }

  @Override
  public void copy(VirtualFile root, String path, String from) throws IOException {
    AbstractVcsTestCase.verify(runSvn(root, "copy", path, from));
  }
}

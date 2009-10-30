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
package com.intellij.openapi.compiler.make;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.descriptors.ConfigFile;
import com.intellij.util.descriptors.CustomConfigFile;
import com.intellij.util.descriptors.ConfigFileMetaData;

import java.io.File;

public abstract class BuildParticipantBase extends BuildParticipant {
  private final Module myModule;

  protected BuildParticipantBase(final Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public void buildStarted(final CompileContext context) {
    new ReadAction() {
      protected void run(final Result result) {
        ConfigFile[] descriptors = getDeploymentDescriptors();
        for (ConfigFile descriptor : descriptors) {
          DeploymentUtil.getInstance().checkConfigFile(descriptor, context, myModule);
        }
      }
    }.execute();
  }
                                   
  protected void registerDescriptorCopyingInstructions(final BuildRecipe instructions, final CompileContext context) {
    final ConfigFile[] deploymentDescriptors = getDeploymentDescriptors();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        registerDescriptorCopyingInstructions(BuildParticipantBase.this.myModule, deploymentDescriptors, instructions);


        final CustomConfigFile[] customDescriptors = getCustomDescriptors();
        for (CustomConfigFile descriptor : customDescriptors) {
          final String url = descriptor.getUrl();
          final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(url);
          if (virtualFile != null) {
            File file = VfsUtil.virtualToIoFile(virtualFile);
            instructions.addFileCopyInstruction(file, false, myModule, descriptor.getOutputDirectoryPath() + "/" + virtualFile.getName(), null);
          }
        }
      }
    });
  }

  public static void registerDescriptorCopyingInstructions(Module module, ConfigFile[] deploymentDescriptors, BuildRecipe instructions) {
    for (ConfigFile descriptor : deploymentDescriptors) {
      VirtualFile virtualFile = descriptor.getVirtualFile();
      if (virtualFile != null) {
        ConfigFileMetaData metaData = descriptor.getMetaData();
        final File file = VfsUtil.virtualToIoFile(virtualFile);
        final String fileName;
        if (metaData.isFileNameFixed()) {
          fileName = metaData.getFileName();
        }
        else {
          fileName = virtualFile.getName();
        }
        instructions.addFileCopyInstruction(file, false, module, metaData.getDirectoryPath() + "/" + fileName, null);
      }

    }
  }

  protected CustomConfigFile[] getCustomDescriptors() {
    return CustomConfigFile.EMPTY_ARRAY;
  }

  protected abstract ConfigFile[] getDeploymentDescriptors();
}

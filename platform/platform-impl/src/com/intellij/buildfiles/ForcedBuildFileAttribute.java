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
package com.intellij.buildfiles;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lene
 * Date: 04.04.11
 * Time: 17:40
 */
public class ForcedBuildFileAttribute {
  private static final Logger LOG = Logger.getInstance("#" + ForcedBuildFileAttribute.class.getName());

  private static final FileAttribute FRAMEWORK_FILE_ATTRIBUTE = new FileAttribute("forcedBuildFileFrameworkAttribute", 1, false);
  private static final Key<String> FRAMEWORK_FILE_MARKER = Key.create("forcedBuildFileFrameworkAttribute");


  private ForcedBuildFileAttribute() {
  }

  public static boolean belongsToFramework(VirtualFile file, @NotNull String frameworkId) {
    return frameworkId.equals(getFrameworkIdOfBuildFile(file));
  }

  @Nullable
  public static String getFrameworkIdOfBuildFile(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      final DataInputStream is = FRAMEWORK_FILE_ATTRIBUTE.readAttribute(file);
      if (is != null) {
        try {
          try {
            /*
        //todo[lene] IOUtil throws   java.io.EOFException
	at java.io.DataInputStream.readFully(DataInputStream.java:180)
	at java.io.DataInputStream.readFully(DataInputStream.java:152)
	at com.intellij.util.io.IOUtil.readString(IOUtil.java:40)
	at com.intellij.buildfiles.ForcedBuildFileAttribute.getFrameworkIdOfBuildFile(ForcedBuildFileAttribute.java:59)
             */
            return is.readUTF();
          }
          finally {
            is.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      return "";
    }
    return file.getUserData(FRAMEWORK_FILE_MARKER);
  }


  public static void forceFileToFramework(VirtualFile file, String frameworkId, boolean value) {
    if (!value && !frameworkId.equals(getFrameworkIdOfBuildFile(file))) {//belongs to other framework
      return;
    }
    forceBuildFile(file, frameworkId);
  }


  private static void forceBuildFile(VirtualFile file, String value) {
    if (file instanceof NewVirtualFile) {
      final DataOutputStream os = FRAMEWORK_FILE_ATTRIBUTE.writeAttribute(file);
      try {
        try {
          os.writeUTF(value);
        }
        finally {
          os.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      file.putUserData(FRAMEWORK_FILE_MARKER, value);
    }
  }
}

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
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class FileCopyElementType extends PackagingElementType<FileCopyPackagingElement> {
  public static final Icon ICON = IconLoader.getIcon("/fileTypes/text.png");

  FileCopyElementType() {
    super("file-copy", "File");
  }

  @Override
  public Icon getCreateElementIcon() {
    return ICON;
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }

  @NotNull
  public List<? extends FileCopyPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                   @NotNull CompositePackagingElement<?> parent) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, true, true, false, true);
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, context.getProject());
    final VirtualFile[] files = chooser.choose(null, context.getProject());
    final List<FileCopyPackagingElement> list = new ArrayList<FileCopyPackagingElement>();
    for (VirtualFile file : files) {
      list.add(new FileCopyPackagingElement(file.getPath()));
    }
    return list;
  }

  @NotNull
  public FileCopyPackagingElement createEmpty(@NotNull Project project) {
    return new FileCopyPackagingElement();
  }
}

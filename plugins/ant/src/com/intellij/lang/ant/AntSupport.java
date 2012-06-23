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
package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.ant.dom.AntDomAntlib;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AntSupport implements ApplicationComponent {

  public AntSupport() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static void markFileAsAntFile(final VirtualFile file, final Project project, final boolean value) {
    if (file.isValid() && ForcedAntFileAttribute.isAntFile(file) != value) {
      ForcedAntFileAttribute.forceAntFile(file, value);
      ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
      restartDaemon(project);
    }
  }
  
  private static void restartDaemon(Project project) {
    final DaemonCodeAnalyzer daemon = DaemonCodeAnalyzer.getInstance(project);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      daemon.restart();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          daemon.restart();
        }
      });
    }
  }
  

  //
  // Managing ant files dependencies via the <import> task.
  //

  @Nullable
  public static AntDomProject getAntDomProject(PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      final DomManager domManager = DomManager.getDomManager(psiFile.getProject());
      final DomFileElement<AntDomProject> fileElement = domManager.getFileElement((XmlFile)psiFile, AntDomProject.class);
      return fileElement != null? fileElement.getRootElement() : null;
    }
    return null;
  }

  @Nullable
  public static AntDomProject getAntDomProjectForceAntFile(PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      final DomManager domManager = DomManager.getDomManager(psiFile.getProject());
      DomFileElement<AntDomProject> fileElement = domManager.getFileElement((XmlFile)psiFile, AntDomProject.class);
      if (fileElement == null) {
        ForcedAntFileAttribute.forceAntFile(psiFile.getVirtualFile(), true);
        fileElement = domManager.getFileElement((XmlFile)psiFile, AntDomProject.class);
      }
      return fileElement != null? fileElement.getRootElement() : null;
    }
    return null;
  }

  @Nullable
  public static AntDomAntlib getAntLib(PsiFile psiFile) {
    if (psiFile instanceof XmlFile) {
      final DomManager domManager = DomManager.getDomManager(psiFile.getProject());
      final DomFileElement<AntDomAntlib> fileElement = domManager.getFileElement((XmlFile)psiFile, AntDomAntlib.class);
      return fileElement != null? fileElement.getRootElement() : null;
    }
    return null;
  }

  @Nullable
  public static AntDomElement getAntDomElement(XmlTag xmlTag) {
    final DomElement domElement = DomManager.getDomManager(xmlTag.getProject()).getDomElement(xmlTag);
    return domElement instanceof AntDomElement? (AntDomElement)domElement : null;
  }

  @Nullable
  public static AntDomElement getInvocationAntDomElement(ConvertContext context) {
    return context.getInvocationElement().getParentOfType(AntDomElement.class, false);
  }
}

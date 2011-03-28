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
package com.intellij.refactoring.copy;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CopyClassesHandler implements CopyHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#" + CopyClassesHandler.class.getName());

  public boolean canCopy(PsiElement[] elements) {
    return canCopyClass(elements);
  }

  public static boolean canCopyClass(PsiElement... elements) {
    return convertToTopLevelClasses(elements, null, null) != null;
  }

  @Nullable
  private static Map<PsiFile, PsiClass[]> convertToTopLevelClasses(final PsiElement[] elements, String relativePath, Map<PsiFile, String> relativeMap) {
    final Map<PsiFile, PsiClass[]> result = new HashMap<PsiFile, PsiClass[]>();
    for (PsiElement element : elements) {
      final PsiFile containingFile = element.getNavigationElement().getContainingFile();
      if (!CollectHighlightsUtil.isOutsideSourceRootJavaFile(containingFile)) {
        PsiClass[] topLevelClasses = getTopLevelClasses(element);
        if (topLevelClasses == null) {
          if (element instanceof PsiDirectory) {
            final String name = ((PsiDirectory)element).getName();
            final String path = relativePath != null ? (relativePath.length() > 0 ? (relativePath + "/") : "") + name : null;
            final Map<PsiFile, PsiClass[]> map = convertToTopLevelClasses(element.getChildren(), path, relativeMap);
            if (map == null) return null;
            for (Map.Entry<PsiFile, PsiClass[]> entry : map.entrySet()) {
              fillResultsMap(result, entry.getKey(), entry.getValue());
            }
            continue;
          }
          return null;
        }
        fillResultsMap(result, containingFile, topLevelClasses);
        if (relativeMap != null) {
          relativeMap.put(containingFile, relativePath);
        }
      }
    }
    return result.isEmpty() ? null : result;
  }

  @Nullable
  private static String normalizeRelativeMap(Map<PsiFile, String> relativeMap) {
    String vector = null;
    for (String relativePath : relativeMap.values()) {
      if (vector == null) {
        vector = relativePath;
      } else if (vector.startsWith(relativePath + "/")) {
        vector = relativePath;
      } else if (!relativePath.startsWith(vector + "/") && !relativePath.equals(vector)) {
        return null;
      }
    }
    if (vector != null) {
      for (PsiFile psiFile : relativeMap.keySet()) {
        final String path = relativeMap.get(psiFile);
        relativeMap.put(psiFile, path.equals(vector) ? "" : path.substring(vector.length() + 1));
      }
    }
    return vector;
  }

  private static void fillResultsMap(Map<PsiFile, PsiClass[]> result, PsiFile containingFile, PsiClass[] topLevelClasses) {
    PsiClass[] classes = result.get(containingFile);
    if (classes != null) {
      topLevelClasses = ArrayUtil.mergeArrays(classes, topLevelClasses, PsiClass.ARRAY_FACTORY);
    }
    result.put(containingFile, topLevelClasses);
  }

  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    final HashMap<PsiFile, String> relativePathsMap = new HashMap<PsiFile, String>();
    final Map<PsiFile, PsiClass[]> classes = convertToTopLevelClasses(elements, "", relativePathsMap);
    assert classes != null;
    if (defaultTargetDirectory == null) {
      defaultTargetDirectory = classes.keySet().iterator().next().getContainingDirectory();
    }
    Project project = defaultTargetDirectory.getProject();
    PsiDirectory targetDirectory = null;
    String className = null;
    if (classes.size() == 1 && classes.values().iterator().next().length == 1) {
      final String commonPath = ArrayUtil.find(elements, classes.values().iterator().next()) == -1 ? normalizeRelativeMap(relativePathsMap) : null;
      CopyClassDialog dialog = new CopyClassDialog(classes.values().iterator().next()[0], defaultTargetDirectory, project, false){
        @Override
        protected String getQualifiedName() {
          if (commonPath != null) {
            return StringUtil.getQualifiedName(super.getQualifiedName(), commonPath.replaceAll("/", "."));
          }
          return super.getQualifiedName();
        }
      };
      dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
      dialog.show();
      if (dialog.isOK()) {
        targetDirectory = dialog.getTargetDirectory();
        className = dialog.getClassName();
        if (className == null || className.length() == 0) return;
      }
    } else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        targetDirectory = defaultTargetDirectory;
      } else {
        final CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(PsiUtilBase.toPsiFileArray(classes.keySet()),
                                                                               defaultTargetDirectory, project, false);
        dialog.show();
        if (dialog.isOK()) {
          targetDirectory = dialog.getTargetDirectory();
        }
      }
    }
    if (targetDirectory != null) {
      copyClassesImpl(className, project, classes, relativePathsMap, targetDirectory, RefactoringBundle.message("copy.handler.copy.class"), false);
    }
  }

  public void doClone(PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass[] classes = getTopLevelClasses(element);
    LOG.assertTrue(classes != null && classes.length == 1);
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(classes[0], null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    dialog.show();
    if (dialog.isOK()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassesImpl(className, project, Collections.singletonMap(classes[0].getContainingFile(), classes), null, targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
    }
  }

  private static void copyClassesImpl(final String copyClassName,
                                      final Project project,
                                      final Map<PsiFile, PsiClass[]> classes,
                                      final HashMap<PsiFile, String> map,
                                      final PsiDirectory targetDirectory,
                                      String commandName,
                                      final boolean selectInActivePanel) {
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiElement newElement = doCopyClasses(classes, map, copyClassName, targetDirectory, project);
              if (newElement != null) {
                CopyHandler.updateSelectionInActiveProjectView(newElement, project, selectInActivePanel);
                EditorHelper.openInEditor(newElement);

                result[0] = true;
              }
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

   @Nullable
  public static PsiElement doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                         final String copyClassName,
                                         final PsiDirectory targetDirectory,
                                         final Project project) throws IncorrectOperationException {
     return doCopyClasses(fileToClasses, null, copyClassName, targetDirectory, project);
   }

  @Nullable
  public static PsiElement doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                         HashMap<PsiFile, String> map, final String copyClassName,
                                         final PsiDirectory targetDirectory,
                                         final Project project) throws IncorrectOperationException {
    PsiElement newElement = null;
    final Map<PsiClass, PsiElement> oldToNewMap = new HashMap<PsiClass, PsiElement>();
    for (final PsiClass[] psiClasses : fileToClasses.values()) {
      for (PsiClass aClass : psiClasses) {
        if (aClass instanceof SyntheticElement) {
          continue;
        }
        oldToNewMap.put(aClass, null);
      }
    }
    final PsiFile[] createdFiles = new PsiFile[fileToClasses.size()];
    int foIdx = 0;
    for (final Map.Entry<PsiFile, PsiClass[]> entry : fileToClasses.entrySet()) {
      final PsiFile createdFile = copy(entry.getKey(), targetDirectory, copyClassName, map == null ? null : map.get(entry.getKey()));
      final PsiClass[] sources = entry.getValue();

      if (createdFile instanceof PsiClassOwner) {
        for (final PsiClass destination : ((PsiClassOwner)createdFile).getClasses()) {
          if (destination instanceof SyntheticElement) {
            continue;
          }
          PsiClass source = findByName(sources, destination.getName());
          if (source != null) {
            final PsiClass copy = copy(source, copyClassName);
            newElement = destination.replace(copy);
            oldToNewMap.put(source, newElement);
          }
          else {
            destination.delete();
          }
        }
      }

      createdFiles[foIdx++] = createdFile;
    }

    final Set<PsiElement> rebindExpressions = new HashSet<PsiElement>();
    for (PsiElement element : oldToNewMap.values()) {
      decodeRefs(element, oldToNewMap, rebindExpressions);
    }

    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    for (PsiFile psiFile : createdFiles) {
      if (psiFile instanceof PsiJavaFile) {
        codeStyleManager.removeRedundantImports((PsiJavaFile)psiFile);
      }
    }
    for (PsiElement expression : rebindExpressions) {
      codeStyleManager.shortenClassReferences(expression);
    }
    new OptimizeImportsProcessor(project, createdFiles, null).run();
    return newElement;
  }

  private static PsiFile copy(@NotNull PsiFile file, PsiDirectory directory, String name, String relativePath) {
    final String fileName = name != null ? (name +  "." + file.getViewProvider().getVirtualFile().getExtension()) : file.getName();
    if (relativePath != null && !relativePath.isEmpty()) {
      return buildRelativeDir(directory, relativePath).findOrCreateTargetDirectory().copyFileFrom(fileName, file);
    }
    return directory.copyFileFrom(fileName, file);
  }

  @NotNull
  private static MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper buildRelativeDir(final @NotNull PsiDirectory directory,
                                                                                           final @NotNull String relativePath) {
    MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper current = null;
    for (String pathElement : relativePath.split("/")) {
      if (current == null) {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(directory, pathElement);
      } else {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(current, pathElement);
      }
    }
    LOG.assertTrue(current != null);
    return current;
  }

  private static PsiClass copy(PsiClass aClass, String name) {
    final PsiClass classNavigationElement = (PsiClass)aClass.getNavigationElement();
    final PsiClass classCopy = (PsiClass)classNavigationElement.copy();
    if (name != null) {
      classCopy.setName(name);
    }
    return classCopy;
  }

  @Nullable
  private static PsiClass findByName(PsiClass[] classes, String name) {
    if (name != null) {
      for (PsiClass aClass : classes) {
        if (name.equals(aClass.getName())) {
          return aClass;
        }
      }
    }
    return null;
  }

  private static void rebindExternalReferences(PsiElement element,
                                               Map<PsiClass, PsiElement> oldToNewMap,
                                               Set<PsiElement> rebindExpressions) {
     final LocalSearchScope searchScope = new LocalSearchScope(element);
     for (PsiClass aClass : oldToNewMap.keySet()) {
       final PsiElement newClass = oldToNewMap.get(aClass);
       for (PsiReference reference : ReferencesSearch.search(aClass, searchScope)) {
         rebindExpressions.add(reference.bindToElement(newClass));
       }
     }
   }


  private static void decodeRefs(PsiElement element, final Map<PsiClass, PsiElement> oldToNewMap, final Set<PsiElement> rebindExpressions) {
    element.accept(new JavaRecursiveElementVisitor(){
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(expression, oldToNewMap, rebindExpressions);
        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement referenceElement = expression.getClassReference();
        if (referenceElement != null) {
          decodeRef(referenceElement, oldToNewMap, rebindExpressions);
        }
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          decodeRef(referenceElement, oldToNewMap, rebindExpressions);
        }
        super.visitTypeElement(type);
      }
    });
    rebindExternalReferences(element, oldToNewMap, rebindExpressions);
  }

  private static void decodeRef(final PsiJavaCodeReferenceElement expression,
                                final Map<PsiClass, PsiElement> oldToNewMap,
                                Set<PsiElement> rebindExpressions) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)resolved;
      if (oldToNewMap.containsKey(psiClass)) {
        rebindExpressions.add(expression.bindToElement(oldToNewMap.get(psiClass)));
      }
    }
  }

  @Nullable
  private static PsiClass[] getTopLevelClasses(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() != null && (((PsiClass)element).getContainingClass() == null)) break;
      element = element.getParent();
    }
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length > 0) {
        for (final PsiClass aClass : classes) {
          if (aClass instanceof SyntheticElement) {
            return null;
          }
        }

        return classes;
      }
    }
    return element instanceof PsiClass ? new PsiClass[]{(PsiClass)element} : null;
  }
}

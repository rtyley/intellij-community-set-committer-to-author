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
package org.jetbrains.idea.devkit.references;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.*;

/**
 * @author Konstantin Bulenkov
 */
public class IconsReferencesContributor extends PsiReferenceContributor implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final StringPattern methodName = string().oneOf("findIcon", "getIcon");
    final PsiMethodPattern method = psiMethod().withName(methodName).definedInClass(IconLoader.class.getName());
    final PsiJavaElementPattern.Capture<PsiLiteralExpression> javaFile
      = literalExpression().and(psiExpression().methodCallParameter(0, method));

    final PsiJavaElementPattern.Capture<PsiLiteralExpression> annotationValue
      = literalExpression().annotationParam("com.intellij.ide.presentation.Presentation", "icon");

    final XmlAttributeValuePattern pluginXml = XmlPatterns.xmlAttributeValue().withLocalName("icon");

    registrar.registerReferenceProvider(annotationValue, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (false && !isIdeaProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new PsiReference[] {
          new PsiReferenceBase<PsiElement>(element, true) {
            @Override
            public PsiElement resolve() {
              String value = (String)((PsiLiteralExpression)element).getValue();
              if (value != null && value.startsWith("AllIcons.")) {
                List<String> path = StringUtil.split(value, ".");
                Project project = element.getProject();
                PsiClass cur = JavaPsiFacade.getInstance(project).findClass("com.intellij.icons.AllIcons",
                                                                            GlobalSearchScope.projectScope(project));
                if (cur == null) return null;

                for (int i = 1; i < path.size() - 1; i++) {
                  cur = cur.findInnerClassByName(path.get(i), false);
                  if (cur == null) return null;
                }

                return cur.findFieldByName(path.get(path.size() - 1), false);
              }

              return null;
            }

            @Override
            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
              PsiElement field = resolve();
              if (field instanceof PsiField) {
                String fqn = ((PsiField)field).getContainingClass().getQualifiedName();

                if (fqn.startsWith("com.intellij.icons.")) {
                  String newValue = "\"" + fqn.substring("com.intellij.icons.".length()) + "." + newElementName + "\"";
                  return getElement().replace(
                    JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(newValue, element.getParent()));
                }
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              if (element instanceof PsiField) {
                String fqn = ((PsiField)element).getContainingClass().getQualifiedName();

                if (fqn.startsWith("com.intellij.icons.")) {
                  String newElementName = ((PsiField)element).getName();
                  String newValue = "\"" + fqn.substring("com.intellij.icons.".length()) + "." + newElementName + "\"";
                  return getElement().replace(
                    JavaPsiFacade.getElementFactory(element.getProject()).createExpressionFromText(newValue, getElement().getParent()));
                }
              }

              return super.bindToElement(element);
            }

            @NotNull
            @Override
            public Object[] getVariants() {
              return EMPTY_ARRAY;
            }
          }
        };
      }
    });

    registrar.registerReferenceProvider(javaFile, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        if (!isIdeaProject(element.getProject())) return PsiReference.EMPTY_ARRAY;
        return new FileReferenceSet(element) {
          @Override
          protected Collection<PsiFileSystemItem> getExtraContexts() {
            final Module icons = ModuleManager.getInstance(element.getProject()).findModuleByName("icons");
            if (icons != null) {
              final ArrayList<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
              final VirtualFile[] roots = ModuleRootManager.getInstance(icons).getSourceRoots();
              final PsiManager psiManager = element.getManager();
              for (VirtualFile root : roots) {
                final PsiDirectory directory = psiManager.findDirectory(root);
                if (directory != null) {
                  result.add(directory);
                }
              }
              return result;
            }
            return super.getExtraContexts();
          }
        }.getAllReferences();
      }
    });

    registrar.registerReferenceProvider(pluginXml, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull ProcessingContext context) {
        return new PsiReference[] {
          new PsiReferenceBase<PsiElement>(element, true) {
            @Override
            public PsiElement resolve() {
              String value = ((XmlAttributeValue)element).getValue();
              if (value.startsWith("AllIcons.")) {
                List<String> path = StringUtil.split(value, ".");
                Project project = element.getProject();
                PsiClass cur = JavaPsiFacade.getInstance(project).findClass("com.intellij.icons.AllIcons",
                                                                           GlobalSearchScope.projectScope(project));
                if (cur == null) return null;

                for (int i = 1; i < path.size() - 1; i++) {
                  cur = cur.findInnerClassByName(path.get(i), false);
                  if (cur == null) return null;
                }

                return cur.findFieldByName(path.get(path.size() - 1), false);
              }
              else if (value.startsWith("/")) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef != null ? lastRef.resolve() : null;
              }
              return null;
            }

            @Override
            public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
              PsiElement element = resolve();
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef.handleElementRename(newElementName);
              }
              else if (element instanceof PsiField) {
                String fqn = ((PsiField)element).getContainingClass().getQualifiedName();

                if (fqn.startsWith("com.intellij.icons.")) {
                  XmlAttribute parent = (XmlAttribute)getElement().getParent();
                  parent.setValue(fqn.substring("com.intellij.icons.".length()) + "." + newElementName);
                  return parent.getValueElement();
                }
              }

              return super.handleElementRename(newElementName);
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
              if (element instanceof PsiFile) {
                FileReference lastRef = new FileReferenceSet(element).getLastReference();
                return lastRef.bindToElement(element);
              }
              else if (element instanceof PsiField) {
                String fqn = ((PsiField)element).getContainingClass().getQualifiedName();

                if (fqn.startsWith("com.intellij.icons.")) {
                  XmlAttribute parent = (XmlAttribute)getElement().getParent();
                  parent.setValue(fqn.substring("com.intellij.icons.".length()) + "." + ((PsiField)element).getName());
                  return parent.getValueElement();
                }
              }

              return super.bindToElement(element);
            }

            @NotNull
            @Override
            public Object[] getVariants() {
              return EMPTY_ARRAY;
            }
          }
        };
      }
    });
  }

  public static boolean isIdeaProject(@Nullable Project project) {
    final VirtualFile baseDir;
    return project != null
           && ("IDEA".equals(project.getName()) || "community".equals(project.getName()))
           && (baseDir = project.getBaseDir()) != null
           && baseDir.findFileByRelativePath("plugins") != null;
  }

  @Override
  public boolean execute(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    final PsiElement file = queryParameters.getElementToSearch();
    if (file instanceof PsiBinaryFile) {
      final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Override
        public Module compute() {
          return ModuleUtil.findModuleForPsiElement(file);
        }
      });

      final VirtualFile image = ((PsiBinaryFile)file).getVirtualFile();
      if (isImage(image) && isIconsModule(module)) {
        final Project project = file.getProject();
        final FindModel model = new FindModel();
        final String path = getPathToImage(image, module);
        if (path == null) return true;
        model.setStringToFind(path);
        model.setCaseSensitive(true);
        model.setFindAll(true);
        model.setWholeWordsOnly(true);
        final List<UsageInfo> usages = FindInProjectUtil.findUsages(model, FindInProjectUtil.getPsiDirectory(model, project), project, false);
        if (!usages.isEmpty()) {
          for (final UsageInfo usage : usages) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                final PsiElement element = usage.getElement();

                final ProperTextRange textRange = usage.getRangeInElement();
                if (element != null && textRange != null) {
                  final PsiElement start = element.findElementAt(textRange.getStartOffset());
                  final PsiElement end = element.findElementAt(textRange.getEndOffset());
                  if (start != null && end != null) {
                    PsiElement value = PsiTreeUtil.findCommonParent(start, end);
                    if (value instanceof PsiJavaToken) {
                      value = value.getParent();
                    }
                    if (value != null) {
                      final FileReference reference = FileReferenceUtil.findFileReference(value);
                      if (reference != null) {
                        consumer.process(reference);
                      }
                    }
                  }
                }
              }
            });
          }
        }
      }
    }
    return true;
  }

  @Nullable
  private static String getPathToImage(VirtualFile image, Module module) {
    final String path = ModuleRootManager.getInstance(module).getSourceRoots()[0].getPath();
    return "/" + FileUtil.getRelativePath(path, image.getPath(), '/');
  }

  private static boolean isIconsModule(Module module) {
    return module != null && "icons".equals(module.getName())
           && ModuleRootManager.getInstance(module).getSourceRoots().length == 1;
  }

  private static boolean isImage(VirtualFile image) {
    final FileTypeManager mgr = FileTypeManager.getInstance();
    return image != null && mgr.getFileTypeByFile(image) == mgr.getFileTypeByExtension("png");
  }
}

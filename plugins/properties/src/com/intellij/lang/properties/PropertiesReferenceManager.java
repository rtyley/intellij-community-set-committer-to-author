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
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.xml.XmlPropertiesIndex;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author max
 */
public class PropertiesReferenceManager {
  private final PsiManager myPsiManager;
  private final DumbService myDumbService;

  public static PropertiesReferenceManager getInstance(Project project) {
    return ServiceManager.getService(project, PropertiesReferenceManager.class);
  }

  public PropertiesReferenceManager(PsiManager psiManager, DumbService dumbService) {
    myPsiManager = psiManager;
    myDumbService = dumbService;
  }

  @NotNull
  public List<PropertiesFile> findPropertiesFiles(@NotNull final Module module, final String bundleName) {
    return findPropertiesFiles(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module), bundleName, BundleNameEvaluator.DEFAULT);
  }

  @NotNull
  public List<PropertiesFile> findPropertiesFiles(@NotNull final GlobalSearchScope searchScope,
                                                  final String bundleName,
                                                  BundleNameEvaluator bundleNameEvaluator) {


    final ArrayList<PropertiesFile> result = new ArrayList<PropertiesFile>();
    processPropertiesFiles(searchScope, new PropertiesFileProcessor() {
      public boolean process(String baseName, PropertiesFile propertiesFile) {
        if (baseName.equals(bundleName)) {
          result.add(propertiesFile);
        }
        return true;
      }
    }, bundleNameEvaluator);
    return result;
  }

  @Nullable
  public PropertiesFile findPropertiesFile(final Module module,
                                           final String bundleName,
                                           final Locale locale) {
    List<PropertiesFile> propFiles = findPropertiesFiles(module, bundleName);
    if (locale != null) {
      for(PropertiesFile propFile: propFiles) {
        if (propFile.getLocale().equals(locale)) {
          return propFile;
        }
      }
    }

    // fallback to default locale
    for(PropertiesFile propFile: propFiles) {
      if (propFile.getLocale().getLanguage().length() == 0 || propFile.getLocale().equals(Locale.getDefault())) {
        return propFile;
      }
    }

    // fallback to any file
    if (!propFiles.isEmpty()) {
      return propFiles.get(0);
    }

    return null;
  }

  public String[] getPropertyFileBaseNames(@NotNull final GlobalSearchScope searchScope, final BundleNameEvaluator bundleNameEvaluator) {
    final ArrayList<String> result = new ArrayList<String>();
    processPropertiesFiles(searchScope, new PropertiesFileProcessor() {
      public boolean process(String baseName, PropertiesFile propertiesFile) {
        result.add(baseName);
        return true;
      }
    }, bundleNameEvaluator);
    return ArrayUtil.toStringArray(result);
  }

  public boolean processAllPropertiesFiles(@NotNull final PropertiesFileProcessor processor) {
    return processPropertiesFiles(GlobalSearchScope.allScope(myPsiManager.getProject()), processor, BundleNameEvaluator.DEFAULT);
  }

  public boolean processPropertiesFiles(@NotNull final GlobalSearchScope searchScope,
                                        @NotNull final PropertiesFileProcessor processor,
                                        @NotNull final BundleNameEvaluator evaluator) {

    boolean result = FileBasedIndex.getInstance()
      .processValues(FileTypeIndex.NAME, PropertiesFileType.INSTANCE, null, new FileBasedIndex.ValueProcessor<Void>() {
        public boolean process(VirtualFile file, Void value) {
          return processFile(file, evaluator, processor);
        }
      }, searchScope);
    if (!result) return false;

    return myDumbService.isDumb() || FileBasedIndex.getInstance()
      .processValues(XmlPropertiesIndex.NAME, XmlPropertiesIndex.MARKER_KEY, null, new FileBasedIndex.ValueProcessor<String>() {
        public boolean process(VirtualFile file, String value) {
          return processFile(file, evaluator, processor);
        }
      }, searchScope);
  }

  private boolean processFile(VirtualFile file, BundleNameEvaluator evaluator, PropertiesFileProcessor processor) {
    final PsiFile psiFile = myPsiManager.findFile(file);
    PropertiesFile propertiesFile = PropertiesUtil.getPropertiesFile(psiFile);
    if (propertiesFile != null) {
      final String qName = evaluator.evaluateBundleName(psiFile);
      if (qName != null) {
        if (!processor.process(qName, propertiesFile)) return false;
      }
    }
    return true;
  }
}

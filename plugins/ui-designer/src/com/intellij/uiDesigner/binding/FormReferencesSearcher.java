package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class FormReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<PsiReference> consumer) {
    if (!scopeCanContainForms(p.getScope())) return true;
    final PsiElement refElement = p.getElementToSearch();
    final PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        if (!refElement.isValid()) return null;
        return refElement.getContainingFile();
      }
    });
    if (psiFile == null) return true;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return true;
    Module module = ProjectRootManager.getInstance(refElement.getProject()).getFileIndex().getModuleForFile(virtualFile);
    if (module == null) return true;
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    final LocalSearchScope filterScope = p.getScope() instanceof LocalSearchScope
                                         ? (LocalSearchScope) p.getScope()
                                         : null;

    if (refElement instanceof PsiPackage) {
      //no need to do anything
      //if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, scope)) return false;
    }
    else if (refElement instanceof PsiClass) {
      if (!processReferencesInUIForms(consumer, (PsiClass)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PsiEnumConstant) {
      if (!processReferencesInUIForms(consumer, (PsiEnumConstant)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PsiField) {
      if (!processReferencesInUIForms(consumer, (PsiField)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof IProperty) {
      if (!processReferencesInUIForms(consumer, (Property)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PropertiesFile) {
      if (!processReferencesInUIForms(consumer, (PropertiesFile)refElement, scope, filterScope)) return false;
    }

    return true;
  }

  private static boolean scopeCanContainForms(SearchScope scope) {
    if (!(scope instanceof LocalSearchScope)) return true;
    LocalSearchScope localSearchScope = (LocalSearchScope) scope;
    final PsiElement[] elements = localSearchScope.getScope();
    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory) return true;
      if (!element.isValid()) continue;
      final PsiFile file = element.getContainingFile();
      if (file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM) return true;
    }
    return false;
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   final PsiClass aClass,
                                                   GlobalSearchScope scope, final LocalSearchScope filterScope) {
    PsiManagerImpl manager = (PsiManagerImpl)aClass.getManager();
    String className = getQualifiedName(aClass);
    return className == null || processReferencesInUIFormsInner(className, aClass, processor, scope, manager, filterScope);
  }

  public static String getQualifiedName(final PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        if (!aClass.isValid()) return null;
        return aClass.getQualifiedName();
      }
    });
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   PsiEnumConstant enumConstant,
                                                   GlobalSearchScope scope, final LocalSearchScope filterScope) {
    PsiManagerImpl manager = (PsiManagerImpl)enumConstant.getManager();
    String className = enumConstant.getName();
    return className == null || processReferencesInUIFormsInner(className, enumConstant, processor, scope, manager, filterScope);

  }

  private static boolean processReferencesInUIFormsInner(String name,
                                                         PsiElement element,
                                                         Processor<PsiReference> processor,
                                                         GlobalSearchScope scope1,
                                                         PsiManagerImpl manager,
                                                         final LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(manager.getProject()).intersectWith(scope1);
    manager.startBatchFilesProcessingMode();

    try {
      List<PsiFile> files = FormClassIndex.findFormsBoundToClass(manager.getProject(), name, scope);

      for (PsiFile file : files) {
        ProgressManager.checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, name, element, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   PsiField field,
                                                   GlobalSearchScope scope1,
                                                   LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(field.getProject()).intersectWith(scope1);
    PsiManagerImpl manager = (PsiManagerImpl)field.getManager();
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return true;
    String fieldName;
    final AccessToken token = ReadAction.start();
    try {
      fieldName = field.getName();
    }
    finally {
      token.finish();
    }
    manager.startBatchFilesProcessingMode();

    try {
      final List<PsiFile> files = FormClassIndex.findFormsBoundToClass(containingClass, scope);

      for (PsiFile file : files) {
        ProgressManager.checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, fieldName, field, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferences(final Processor<PsiReference> processor, final PsiFile file, String name, final PsiElement element,
                                           final LocalSearchScope filterScope) {
    CharSequence chars = ApplicationManager.getApplication().runReadAction(new NullableComputable<CharSequence>() {
      public CharSequence compute() {
        if (filterScope != null) {
          boolean isInScope = false;
          for(PsiElement filterElement: filterScope.getScope()) {
            if (PsiTreeUtil.isAncestor(filterElement, file, false)) {
              isInScope = true;
              break;
            }
          }
          if (!isInScope) return null;
        }
        return file.getViewProvider().getContents();
    }});
    if (chars == null) return true;
    int index = 0;
    final int offset = name.lastIndexOf('.');
    while(true){
      index = CharArrayUtil.indexOf(chars, name, index);

      if (index < 0) break;
      final int finalIndex = index;
      final Boolean searchDone = ApplicationManager.getApplication().runReadAction(new NullableComputable<Boolean>() {
        public Boolean compute() {
          final PsiReference ref = file.findReferenceAt(finalIndex + offset + 1);
          if (ref != null && ref.isReferenceTo(element)) {
            return processor.process(ref);
          }
          return true;
        }
      });
      if (!searchDone.booleanValue()) return false;
      index++;
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<PsiReference> processor,
                                                   final Property property,
                                                   final GlobalSearchScope globalSearchScope,
                                                   final LocalSearchScope filterScope) {

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(property.getProject()).intersectWith(globalSearchScope);
    final PsiManagerImpl manager = (PsiManagerImpl)property.getManager();
    String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return property.getName();
      }
    });
    if (name == null) return true;

    manager.startBatchFilesProcessingMode();

    try {
      final List<String> words = StringUtil.getWordsIn(name);
      if(words.isEmpty()) return true;

      final Set<PsiFile> fileSet = new HashSet<PsiFile>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          PsiFile[] filesWithWord = manager.getCacheManager().getFilesWithWord(words.get(0), UsageSearchContext.IN_PLAIN_TEXT, scope, true);
          ContainerUtil.addAll(fileSet, filesWithWord);
          for (int i = 1; i < words.size(); i++) {
            ProgressManager.checkCanceled();
            String word = words.get(i);
            PsiFile[] filesWithThisWord = manager.getCacheManager().getFilesWithWord(word, UsageSearchContext.IN_PLAIN_TEXT, scope, true);
            fileSet.retainAll(Arrays.asList(filesWithThisWord));
            if (fileSet.isEmpty()) break;
          }
        }
      });
      PsiFile[] files = PsiUtilBase.toPsiFileArray(fileSet);

      for (PsiFile file : files) {
        ProgressManager.checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, name, property, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<PsiReference> processor, final PropertiesFile propFile, final GlobalSearchScope globalSearchScope,
                                                   final LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(propFile.getProject()).intersectWith(globalSearchScope);
    PsiManagerImpl manager = (PsiManagerImpl)propFile.getContainingFile().getManager();
    final String baseName = propFile.getResourceBundle().getBaseName();
    manager.startBatchFilesProcessingMode();

    try {
      PsiFile[] files = manager.getCacheManager().getFilesWithWord(baseName, UsageSearchContext.IN_PLAIN_TEXT, scope, true);

      for (PsiFile file : files) {
        ProgressManager.checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, baseName, propFile.getContainingFile(), filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }
}

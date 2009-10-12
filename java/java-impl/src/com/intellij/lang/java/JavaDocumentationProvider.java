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

package com.intellij.lang.java;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 11, 2006
 * Time: 7:45:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaDocumentationProvider implements CodeDocumentationProvider {
  private static final String LINE_SEPARATOR = "\n";

  @NonNls private static final String PARAM_TAG = "@param";
  @NonNls private static final String RETURN_TAG = "@return";
  @NonNls private static final String THROWS_TAG = "@throws";
  @NonNls public static final String HTML_EXTENSION = ".html";
  @NonNls public static final String PACKAGE_SUMMARY_FILE = "package-summary.html";

  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof PsiClass) {
      return generateClassInfo((PsiClass)element);
    }
    else if (element instanceof PsiMethod) {
      return generateMethodInfo((PsiMethod)element);
    }
    else if (element instanceof PsiField) {
      return generateFieldInfo((PsiField)element);
    }
    else if (element instanceof PsiVariable) {
      return generateVariableInfo((PsiVariable)element);
    }
    else if (element instanceof PsiPackage) {
      return generatePackageInfo((PsiPackage)element);
    }
    else if (element instanceof BeanPropertyElement) {
      return generateMethodInfo(((BeanPropertyElement) element).getMethod());
    }
    return null;
  }

  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return getExternalJavaDocUrl(element);
  }

  private static void newLine(StringBuffer buffer) {
    // Don't know why space has to be added after newline for good text alignment...
    buffer.append("\n ");
  }

  private static void generateType(@NonNls StringBuffer buffer, PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      buffer.append(type.getCanonicalText());

      return;
    }

    if (type instanceof PsiWildcardType) {
      PsiWildcardType wc = ((PsiWildcardType)type);
      PsiType bound = wc.getBound();

      buffer.append("?");

      if (bound != null) {
        buffer.append(wc.isExtends() ? " extends " : " super ");
        generateType(buffer, bound, context);
      }
    }

    if (type instanceof PsiArrayType) {
      generateType(buffer, ((PsiArrayType)type).getComponentType(), context);
      if (type instanceof PsiEllipsisType) {
        buffer.append("...");
      }
      else {
        buffer.append("[]");
      }

      return;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null || psiClass instanceof PsiTypeParameter) {
        buffer.append(type.getPresentableText());
        return;
      }

      buffer.append(JavaDocUtil.getShortestClassName(psiClass, context));

      if (psiClass.hasTypeParameters()) {
        StringBuffer subst = new StringBuffer();
        boolean goodSubst = true;

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append("<");
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          generateType(subst, t, context);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        if (goodSubst) {
          subst.append(">");
          String text = subst.toString();

          buffer.append(text);
        }
      }
    }
  }

  private static void generateInitializer(StringBuffer buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      String text = initializer.getText().trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      text = text.substring(0, index);
      buffer.append(" = ");
      buffer.append(text);
      if (trunc) {
        buffer.append("...");
      }
    }
  }

  private static void generateModifiers(StringBuffer buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateClassInfo(PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();

    if (aClass instanceof PsiAnonymousClass) return LangBundle.message("java.terms.anonymous.class");

    PsiFile file = aClass.getContainingFile();
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      buffer.append('[').append(module.getName()).append("] ");
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }

    generateModifiers(buffer, aClass);

    final String classString = aClass.isAnnotationType() ? "java.terms.annotation.interface"
                               : aClass.isInterface()
                                 ? "java.terms.interface"
                                 : aClass instanceof PsiTypeParameter
                                   ? "java.terms.type.parameter"
                                   : aClass.isEnum() ? "java.terms.enum" : "java.terms.class";
    buffer.append(LangBundle.message(classString)).append(" ");

    buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] parms = aClass.getTypeParameters();

      buffer.append("<");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], aClass);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs;
    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      refs = extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes();
      if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
        buffer.append(" extends ");
        if (refs.length == 0) {
          buffer.append("Object");
        }
        else {
          for (int i = 0; i < refs.length; i++) {
            generateType(buffer, refs[i], aClass);

            if (i < refs.length - 1) {
              buffer.append(", ");
            }
          }
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass);

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String generateMethodInfo(PsiMethod method) {
    StringBuffer buffer = new StringBuffer();

    PsiClass parentClass = method.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
      newLine(buffer);
    }

    generateModifiers(buffer, method);

    PsiTypeParameter[] params = method.getTypeParameters();

    if (params.length > 0) {
      buffer.append("<");
      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter param = params[i];

        buffer.append(param.getName());

        PsiClassType[] extendees = param.getExtendsList().getReferencedTypes();

        if (extendees.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < extendees.length; j++) {
            generateType(buffer, extendees[j], method);

            if (j < extendees.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append("> ");
    }

    if (method.getReturnType() != null) {
      generateType(buffer, method.getReturnType(), method);
      buffer.append(" ");
    }

    buffer.append(method.getName());

    buffer.append(" (");
    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateType(buffer, parm.getType(), method);
      buffer.append(" ");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(", ");
      }
    }

    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append(" throws ");
      for (int i = 0; i < refs.length; i++) {
        PsiClass throwsClass = refs[i].resolve();

        if (throwsClass != null) {
          buffer.append(JavaDocUtil.getShortestClassName(throwsClass, method));
        }
        else {
          buffer.append(refs[i].getPresentableText());
        }

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  private static String generateFieldInfo(PsiField field) {
    StringBuffer buffer = new StringBuffer();
    PsiClass parentClass = field.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, field));
      newLine(buffer);
    }

    generateModifiers(buffer, field);

    generateType(buffer, field.getType(), field);
    buffer.append(" ");
    buffer.append(field.getName());

    generateInitializer(buffer, field);

    return buffer.toString();
  }

  private static String generateVariableInfo(PsiVariable variable) {
    StringBuffer buffer = new StringBuffer();

    generateModifiers(buffer, variable);

    generateType(buffer, variable.getType(), variable);

    buffer.append(" ");

    buffer.append(variable.getName());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }

  public PsiComment findExistingDocComment(final PsiComment _element) {
    PsiElement parentElement = _element.getParent();

    return parentElement instanceof PsiDocCommentOwner ? ((PsiDocCommentOwner)parentElement).getDocComment() : null;
  }

  public String generateDocumentationContentStub(PsiComment _element) {
    PsiElement parentElement = _element.getParent();
    final Project project = _element.getProject();
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (parentElement instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)parentElement;
        final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE
          .forLanguage(parentElement.getLanguage());
        final Map<String, String> param2Description = new HashMap<String, String>();
        final PsiMethod[] superMethods = psiMethod.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          final PsiDocComment comment = superMethod.getDocComment();
          if (comment != null) {
            final PsiDocTag[] params = comment.findTagsByName("param");
            for (PsiDocTag param : params) {
              final PsiElement[] dataElements = param.getDataElements();
              if (dataElements != null) {
                String paramName = null;
                for (PsiElement dataElement : dataElements) {
                  if (dataElement instanceof PsiDocParamRef) {
                    paramName = dataElement.getReference().getCanonicalText();
                    break;
                  }
                }
                if (paramName != null) {
                  param2Description.put(paramName, param.getText());
                }
              }
            }
          }
        }
        for (PsiParameter parameter : parameters) {
          String description = param2Description.get(parameter.getName());
          if (description != null) {
            builder.append(CodeDocumentationUtil.createDocCommentLine("", project, commenter));
            if (description.indexOf('\n') > -1) description = description.substring(0, description.lastIndexOf('\n'));
            builder.append(description);
          }
          else {
            builder.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
            builder.append(parameter.getName());
          }
          builder.append(LINE_SEPARATOR);
        }

        final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
        if (typeParameterList != null) {
          createTypeParamsListComment(builder, project, commenter, typeParameterList);
        }
        if (psiMethod.getReturnType() != null && psiMethod.getReturnType() != PsiType.VOID) {
          builder.append(CodeDocumentationUtil.createDocCommentLine(RETURN_TAG, project, commenter));
          builder.append(LINE_SEPARATOR);
        }

        final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
        for (PsiJavaCodeReferenceElement reference : references) {
          builder.append(CodeDocumentationUtil.createDocCommentLine(THROWS_TAG, project, commenter));
          builder.append(reference.getText());
          builder.append(LINE_SEPARATOR);
        }
      }
      else if (parentElement instanceof PsiClass) {
        final PsiTypeParameterList typeParameterList = ((PsiClass)parentElement).getTypeParameterList();
        if (typeParameterList != null) {
          final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE
            .forLanguage(parentElement.getLanguage());
          createTypeParamsListComment(builder, project, commenter, typeParameterList);
        }
      }
      return builder.length() > 0 ? builder.toString() : null;
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private static void createTypeParamsListComment(final StringBuilder buffer,
                                                  final Project project,
                                                  final CodeDocumentationAwareCommenter commenter,
                                                  final PsiTypeParameterList typeParameterList) {
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      buffer.append(CodeDocumentationUtil.createDocCommentLine(PARAM_TAG, project, commenter));
      buffer.append("<").append(typeParameter.getName()).append(">");
      buffer.append(LINE_SEPARATOR);
    }
  }

  public String generateDoc(final PsiElement element, final PsiElement originalElement) {
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }


    //external documentation finder
    return generateExternalJavadoc(element);
  }

  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  @Nullable
  public static String generateExternalJavadoc(final PsiElement element) {
    final JavaDocInfoGenerator javaDocInfoGenerator = new JavaDocInfoGenerator(element.getProject(), element);
    JavaDocExternalFilter docFilter = new JavaDocExternalFilter(element.getProject());
    List<String> docURLs = getExternalJavaDocUrl(element);

    if (docURLs != null) {
      for (String docURL : docURLs) {
        final String javadoc = generateExternalJavadoc(element, docURL, true, docFilter);
        if (javadoc != null) return javadoc;
      }
    }

    return JavaDocExternalFilter.filterInternalDocInfo(javaDocInfoGenerator.generateDocInfo());
  }


  @Nullable
  private static String generateExternalJavadoc(final PsiElement element, String fromUrl, boolean checkCompiled, JavaDocExternalFilter filter) {
    if (!checkCompiled || element instanceof PsiCompiledElement) {
      try {
        String externalDoc = filter.getExternalDocInfoForElement(fromUrl, element);
        if (externalDoc != null && externalDoc.length() > 0) {
          return externalDoc;
        }
      }
      catch (Exception e) {
        //try to generate some javadoc
      }
    }
    return null;
  }

  private String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);
    final String text = expr.getText();
    if (candidates.length > 0) {
      @NonNls final StringBuffer sb = new StringBuffer();

      for (final CandidateInfo candidate : candidates) {
        final PsiElement element = candidate.getElement();

        if (!(element instanceof PsiMethod)) {
          continue;
        }

        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
                                                      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_PARAMETERS,
                                                      PsiFormatUtil.SHOW_TYPE);
        createElementLink(sb, element, str);
      }

      return CodeInsightBundle.message("javadoc.candiates", text, sb);
    }

    return CodeInsightBundle.message("javadoc.candidates.not.found", text);
  }

  private void createElementLink(@NonNls final StringBuffer sb, final PsiElement element, final String str) {
    sb.append("&nbsp;&nbsp;<a href=\"psi_element://");
    sb.append(JavaDocUtil.getReferenceText(element.getProject(), element));
    sb.append("\">");
    sb.append(str);
    sb.append("</a>");
    sb.append("<br>");
  }

  public boolean isExternalDocumentationEnabled(final PsiElement element, final PsiElement originalElement) {
    boolean actionEnabled = element != null && getExternalJavaDocUrl(element) != null;
    if (element instanceof PsiVariable && !(element instanceof PsiField)) {
      actionEnabled = false;
    }
    return actionEnabled;
  }

  public String getExternalDocumentation(@NotNull final String url, final Project project) throws Exception {
    if (JavaDocExternalFilter.isJavaDocURL(url)) {
      String text = new JavaDocExternalFilter(project).getExternalDocInfo(url);
      if (text != null) {
        return text;
      }
    }
    return null;
  }

  public void openExternalDocumentation(final PsiElement element, final PsiElement originalElement) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.javadoc.external");
    List<String> urls = getExternalJavaDocUrl(element);
    if (urls != null && !urls.isEmpty()) {
      final JavaDocExternalFilter filter = new JavaDocExternalFilter(element.getProject());
      for (Iterator<String> it = urls.iterator(); it.hasNext();) {
        String url = it.next();
        if (generateExternalJavadoc(element, url, false, filter) == null) it.remove();
      }
      final HashSet<String> set = new HashSet<String>(urls);
      if (set.size() > 1) {
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<String>("Choose javadoc root", ArrayUtil.toStringArray(set)) {
          public PopupStep onChosen(final String selectedValue, final boolean finalChoice) {
            BrowserUtil.launchBrowser(selectedValue);
            return PopupStep.FINAL_CHOICE;
          }
        }).showInBestPositionFor(DataManager.getInstance().getDataContext());
      }
      else if (set.size() == 1){
        BrowserUtil.launchBrowser(urls.get(0));
      }
    }
    else {
      final JBPopup docInfoHint = DocumentationManager.getInstance(element.getProject()).getDocInfoHint();
      if (docInfoHint != null && docInfoHint.isVisible()) {
        docInfoHint.cancel();
      }
      Messages.showMessageDialog(element.getProject(), CodeInsightBundle.message("javadoc.documentation.not.found.message"),
                                 CodeInsightBundle.message("javadoc.documentation.not.found.title"), Messages.getErrorIcon());
    }
  }

  @Nullable
  public static List<String> getExternalJavaDocUrl(final PsiElement element) {
    List<String> urls = null;

    if (element instanceof PsiClass) {
      urls = findUrlForClass((PsiClass)element);
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      if (aClass != null) {
        urls = findUrlForClass(aClass);
        if (urls != null) {
          for (int i = 0; i < urls.size(); i++) {
            urls.set(i, urls.get(i) + "#" + field.getName());
          }
        }
      }
    }
    else if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiClass aClass = method.getContainingClass();
      if (aClass != null) {
        final List<String> classUrls = findUrlForClass(aClass);

        if (classUrls != null) {
          urls = new ArrayList<String>();
          String signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_RAW_TYPE,
                                                        PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES | PsiFormatUtil.SHOW_RAW_TYPE, 999);
          for (String classUrl : classUrls) {
            urls.add(classUrl + "#" + signature);
          }
          signature = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                                        PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_FQ_CLASS_NAMES, 999);
          for (String classUrl : classUrls) {
            urls.add(classUrl + "#" + signature);
          }
        }
      }
    }
    else if (element instanceof PsiPackage) {
      urls = findUrlForPackage((PsiPackage)element);
    }
    else if (element instanceof PsiDirectory) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)element));
      if (aPackage != null) {
        urls = findUrlForPackage(aPackage);
      }
    }

    if (urls == null) {
      return null;
    }
    else {
      for (int i = 0; i < urls.size(); i++) {
        urls.set(i, FileUtil.toSystemIndependentName(urls.get(i)));
      }
      return urls;
    }
  }

  @Nullable
  public static List<String> findUrlForClass(PsiClass aClass) {
    String qName = aClass.getQualifiedName();
    if (qName == null) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    String packageName = ((PsiJavaFile)file).getPackageName();

    String relPath;
    if (packageName.length() > 0) {
      relPath = packageName.replace('.', '/') + '/' + qName.substring(packageName.length() + 1) + HTML_EXTENSION;
    }
    else {
      relPath = qName + HTML_EXTENSION;
    }

    final PsiFile containingFile = aClass.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;

    return findUrlForVirtualFile(containingFile.getProject(), virtualFile, relPath);
  }

  @Nullable
  public static List<String> findUrlForVirtualFile(final Project project, final VirtualFile virtualFile, final String relPath) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(virtualFile);
    if (module == null) {
      final VirtualFileSystem fs = virtualFile.getFileSystem();
      if (fs instanceof JarFileSystem) {
        final VirtualFile jar = ((JarFileSystem)fs).getVirtualFileForJar(virtualFile);
        if (jar != null) {
          module = fileIndex.getModuleForFile(jar);
        }
      }
    }
    if (module != null) {
      String[] javadocPaths = ModuleRootManager.getInstance(module).getRootUrls(JavadocOrderRootType.getInstance());
      List<String> httpRoot = getHttpRoots(javadocPaths, relPath);
      if (httpRoot != null) return httpRoot;
    }

    final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(virtualFile);
    for (OrderEntry orderEntry : orderEntries) {
      final String[] files = orderEntry.getUrls(JavadocOrderRootType.getInstance());
      final List<String> httpRoot = getHttpRoots(files, relPath);
      if (httpRoot != null) return httpRoot;
    }
    return null;
  }

  @Nullable
  public static List<String> getHttpRoots(final String[] roots, String relPath) {
    final ArrayList<String> result = new ArrayList<String>();
    for (String root : roots) {
      final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(root);
      if (virtualFile != null) {
        if (virtualFile.getFileSystem() instanceof HttpFileSystem) {
          String url = virtualFile.getUrl();
          if (!url.endsWith("/")) url += "/";
          result.add(url + relPath);
        }
        else {
          VirtualFile file = virtualFile.findFileByRelativePath(relPath);
          if (file != null) result.add(file.getUrl());
        }
      }
    }

    return result.isEmpty() ? null : result;
  }

  @Nullable
  public static List<String> findUrlForPackage(PsiPackage aPackage) {
    String qName = aPackage.getQualifiedName();
    qName = qName.replace('.', '/') + '/' + PACKAGE_SUMMARY_FILE;
    for (PsiDirectory directory : aPackage.getDirectories()) {
      List<String> url = findUrlForVirtualFile(aPackage.getProject(), directory.getVirtualFile(), qName);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }
}

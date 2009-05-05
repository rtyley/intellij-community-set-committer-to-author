package com.intellij.appengine.inspections;

import com.intellij.appengine.sdk.AppEngineSdkManager;
import com.intellij.appengine.server.integration.AppEngineServerIntegration;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.javaee.appServerIntegrations.ApplicationServer;
import com.intellij.javaee.facet.JavaeeFacetUtil;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineForbiddenCodeInspection extends BaseJavaLocalInspectionTool {

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, boolean isOnTheFly) {
    final List<ApplicationServer> servers = ApplicationServersManager.getInstance().getApplicationServers(AppEngineServerIntegration.getInstance());
    if (servers.isEmpty()) {
      return null;
    }

    final Project project = manager.getProject();
    final WebFacet webFacet = JavaeeFacetUtil.getInstance().getJavaeeFacet(file.getVirtualFile(), WebFacet.ID, project);
    if (webFacet == null) {
      return null;
    }
    if (!AppEngineUtil.isAppEngineSupportEnabled(webFacet)) return null;

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final AppEngineSdkManager sdkManager = AppEngineSdkManager.getInstance();
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitDocComment(PsiDocComment comment) {
      }

      @Override
      public void visitMethod(PsiMethod method) {
        final PsiModifierList modifierList = method.getModifierList();
        if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
          problems.add(manager.createProblemDescriptor(modifierList, "Native methods aren't allowed in App Engine application",
                                                       LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        super.visitMethod(method);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) {
          final PsiElement resolved = classReference.resolve();
          if (resolved instanceof PsiClass) {
            final String qualifiedName = ((PsiClass)resolved).getQualifiedName();
            if (qualifiedName != null && sdkManager.isMethodInBlacklist(qualifiedName, "new")) {
              final String message = "App Engine application should not create new instances of '" + qualifiedName + "' class";
              problems.add(manager.createProblemDescriptor(classReference, message, LocalQuickFix.EMPTY_ARRAY,
                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
        super.visitNewExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement element = methodExpression.resolve();
        if (element instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element;
          if (method.getModifierList().hasModifierProperty(PsiModifier.NATIVE)) {
            problems.add(manager.createProblemDescriptor(methodExpression, "App Engine application should not call native methods",
                                                         LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
          else {
            final PsiClass psiClass = method.getContainingClass();
            if (psiClass != null) {
              final String qualifiedName = psiClass.getQualifiedName();
              final String methodName = method.getName();
              if (qualifiedName != null && sdkManager.isMethodInBlacklist(qualifiedName, methodName)) {
                final String message =
                    "AppEngine application should not call '" + StringUtil.getShortName(qualifiedName) + "." + methodName + "' method";
                problems.add(manager.createProblemDescriptor(methodExpression, message, LocalQuickFix.EMPTY_ARRAY,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              }
            }
          }
        }
        super.visitMethodCallExpression(expression);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
          final PsiFile psiFile = resolved.getContainingFile();
          if (psiFile != null) {
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null && !fileIndex.isInSource(virtualFile)) {
              final List<OrderEntry> list = fileIndex.getOrderEntriesForFile(virtualFile);
              for (OrderEntry entry : list) {
                if (entry instanceof JdkOrderEntry) {
                  final String className = ((PsiClass)resolved).getQualifiedName();
                  if (className != null && !sdkManager.isClassInWhiteList(className)) {
                    problems.add(manager.createProblemDescriptor(reference, "Class '" + className + "' is not included in App Engine JRE White List", LocalQuickFix.EMPTY_ARRAY,
                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                  }
                }
              }
            }
          }
        }
        super.visitReferenceElement(reference);
      }
    });
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return "Google App Engine";
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Forbidden code in App Engine applications";
  }

  @NotNull
  public String getShortName() {
    return "AppEngineForbiddenCode";
  }
}

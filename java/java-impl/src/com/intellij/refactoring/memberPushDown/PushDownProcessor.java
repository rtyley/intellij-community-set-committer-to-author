package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.JavaRefactoringListenerManager;
import com.intellij.refactoring.listeners.impl.JavaRefactoringListenerManagerImpl;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PushDownProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPushDown.PushDownProcessor");
  private final MemberInfo[] myMemberInfos;
  private PsiClass myClass;
  private final DocCommentPolicy myJavaDocPolicy;

  public PushDownProcessor(Project project,
                           MemberInfo[] memberInfos,
                           PsiClass aClass,
                           DocCommentPolicy javaDocPolicy) {
    super(project);
    myMemberInfos = memberInfos;
    myClass = aClass;
    myJavaDocPolicy = javaDocPolicy;
  }

  protected String getCommandName() {
    return JavaPushDownHandler.REFACTORING_NAME;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new PushDownUsageViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiClass[] inheritors = ClassInheritorsSearch.search(myClass, myClass.getUseScope(), false).toArray(PsiClass.EMPTY_ARRAY);
    UsageInfo[] usages = new UsageInfo[inheritors.length];
    for (int i = 0; i < inheritors.length; i++) {
      usages[i] = new UsageInfo(inheritors[i]);
    }
    return usages;
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    final PushDownConflicts pushDownConflicts = new PushDownConflicts(myClass, myMemberInfos);
    pushDownConflicts.checkSourceClassConflicts();

    if (usagesIn.length == 0) {
      String noInheritors = myClass.isInterface() ?
                            RefactoringBundle.message("interface.0.does.not.have.inheritors", myClass.getQualifiedName()) :
                            RefactoringBundle.message("class.0.does.not.have.inheritors", myClass.getQualifiedName());
      final String message = noInheritors + "\n" + RefactoringBundle.message("push.down.will.delete.members");
      final int answer = Messages.showYesNoDialog(message, JavaPushDownHandler.REFACTORING_NAME, Messages.getWarningIcon());
      if (answer != 0) return false;
    }
    for (UsageInfo usage : usagesIn) {
      final PsiElement element = usage.getElement();
      if (element instanceof PsiClass) {
        pushDownConflicts.checkTargetClassConflicts((PsiClass)element);
      }
    }

    return showConflicts(pushDownConflicts.getConflicts());
  }

  protected void refreshElements(PsiElement[] elements) {
    if(elements.length == 1 && elements[0] instanceof PsiClass) {
      myClass = (PsiClass) elements[0];
    }
    else {
      LOG.assertTrue(false);
    }
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      encodeRefs();
      for (UsageInfo usage : usages) {
        if (usage.getElement() instanceof PsiClass) {
          final PsiClass targetClass = (PsiClass)usage.getElement();
          pushDownToClass(targetClass);
        }
      }
      removeFromTargetClass();
    }
    catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
    }
  }

  private static final Key<Boolean> REMOVE_QUALIFIER_KEY = Key.create("REMOVE_QUALIFIER_KEY");
  private static final Key<PsiClass> REPLACE_QUALIFIER_KEY = Key.create("REPLACE_QUALIFIER_KEY");

  private void encodeRefs() {
    final Set<PsiMember> movedMembers = new HashSet<PsiMember>();
    for (MemberInfo memberInfo : myMemberInfos) {
      movedMembers.add(memberInfo.getMember());
    }

    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      member.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          encodeRef(expression, movedMembers, expression);
          super.visitReferenceExpression(expression);
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
          if (classReference != null) {
            encodeRef(classReference, movedMembers, expression);
          }
          super.visitNewExpression(expression);
        }

        @Override
        public void visitTypeElement(final PsiTypeElement type) {
          final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
          if (referenceElement != null) {
            encodeRef(referenceElement, movedMembers, type);
          }
          super.visitTypeElement(type);
        }
      });
      ChangeContextUtil.encodeContextInfo(member, false);
    }
  }

  private void encodeRef(final PsiJavaCodeReferenceElement expression, final Set<PsiMember> movedMembers, final PsiElement toPut) {
    final PsiElement resolved = expression.resolve();
    if (resolved == null) return;
    final PsiElement qualifier = expression.getQualifier();
    for (PsiMember movedMember : movedMembers) {
      if (movedMember.equals(resolved)) {
        if (qualifier == null) {
          toPut.putCopyableUserData(REMOVE_QUALIFIER_KEY, Boolean.TRUE);
        } else {
          if (qualifier instanceof PsiJavaCodeReferenceElement &&
              ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(myClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
          }
        }
      } else if (movedMember instanceof PsiClass && PsiTreeUtil.getParentOfType(resolved, PsiClass.class, false) == movedMember) {
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(movedMember)) {
          toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, (PsiClass)movedMember);
        }
      } else {
        if (qualifier instanceof PsiThisExpression) {
          final PsiJavaCodeReferenceElement qElement = ((PsiThisExpression)qualifier).getQualifier();
          if (qElement != null && qElement.isReferenceTo(myClass)) {
            toPut.putCopyableUserData(REPLACE_QUALIFIER_KEY, myClass);
          }
        }
      }
    }
  }

  private void decodeRefs(final PsiMember member, final PsiClass targetClass) {
    try {
      ChangeContextUtil.decodeContextInfo(member, null, null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    member.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(expression, factory, targetClass, expression);
        super.visitReferenceExpression(expression);
      }

      @Override public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference != null) decodeRef(classReference, factory, targetClass, expression);
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(final PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null)  decodeRef(referenceElement, factory, targetClass, type);
        super.visitTypeElement(type);
      }
    });
  }

  private void decodeRef(final PsiJavaCodeReferenceElement ref,
                         final PsiElementFactory factory,
                         final PsiClass targetClass,
                         final PsiElement toGet) {
    try {
      if (toGet.getCopyableUserData(REMOVE_QUALIFIER_KEY) != null) {
        toGet.putCopyableUserData(REMOVE_QUALIFIER_KEY, null);
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier != null) qualifier.delete();
      }
      else {
        PsiClass psiClass = toGet.getCopyableUserData(REPLACE_QUALIFIER_KEY);
        if (psiClass != null) {
          toGet.putCopyableUserData(REPLACE_QUALIFIER_KEY, null);
          PsiElement qualifier = ref.getQualifier();
          if (qualifier != null) {

            if (psiClass == myClass) {
              psiClass = targetClass;
            } else if (psiClass.getContainingClass() == myClass) {
              psiClass = targetClass.findInnerClassByName(psiClass.getName(), false);
              LOG.assertTrue(psiClass != null);
            }

            if (!(qualifier instanceof PsiThisExpression) && ref instanceof PsiReferenceExpression) {
              ((PsiReferenceExpression)ref).setQualifierExpression(factory.createReferenceExpression(psiClass));
            }
            else {
              if (qualifier instanceof PsiThisExpression) {
                qualifier = ((PsiThisExpression)qualifier).getQualifier();
              }
              qualifier.replace(factory.createReferenceElementByType(factory.createType(psiClass)));
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void removeFromTargetClass() throws IncorrectOperationException {
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiElement member = memberInfo.getMember();

      if (member instanceof PsiField) {
        member.delete();
      }
      else if (member instanceof PsiMethod) {
        if (memberInfo.isToAbstract()) {
          final PsiMethod method = (PsiMethod)member;
          if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            PsiUtil.setModifierProperty(method, PsiModifier.PROTECTED, true);
          }
          RefactoringUtil.abstractizeMethod(myClass, method);
          myJavaDocPolicy.processOldJavaDoc(method.getDocComment());
        }
        else {
          member.delete();
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          RefactoringUtil.removeFromReferenceList(myClass.getImplementsList(), (PsiClass)member);
        }
        else {
          member.delete();
        }
      }
    }
  }


  private void pushDownToClass(PsiClass targetClass) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, targetClass, PsiSubstitutor.EMPTY);
    for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(myClass)) {
      for (PsiReference reference : ReferencesSearch.search(parameter)) {
        final PsiElement element = reference.getElement();
        final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
        if (member != null) {
          for (MemberInfo memberInfo : myMemberInfos) {
            if (PsiTreeUtil.isAncestor(memberInfo.getMember(), member, false)) {
              final PsiType substitutedType = substitutor.substitute(parameter);
              if (substitutedType != null) {
                element.getParent().replace(factory.createTypeElement(substitutedType));
              }
              break;
            }
          }
        }
      }
    }
    for (MemberInfo memberInfo : myMemberInfos) {
      final PsiMember member = memberInfo.getMember();
      PsiMember newMember = null;
      if (member instanceof PsiField) {
        ((PsiField)member).normalizeDeclaration();
        newMember = (PsiMember)targetClass.add(member);
      }
      else if (member instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)member;

        final PsiMethod methodBySignature = targetClass.findMethodBySignature(method, false);
        if (methodBySignature == null) {
          newMember = (PsiMethod)targetClass.add(method);
          if (memberInfo.isToAbstract()) {
            if (newMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              PsiUtil.setModifierProperty(newMember, PsiModifier.PROTECTED, true);
            }
            myJavaDocPolicy.processNewJavaDoc(((PsiMethod)newMember).getDocComment());
          }
        } else { //abstract method: remove @Override
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(methodBySignature, "java.lang.Override");
          if (annotation != null) {
            annotation.delete();
          }
        }
      }
      else if (member instanceof PsiClass) {
        if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
          final PsiClass aClass = (PsiClass)member;
          if (!targetClass.isInheritor(aClass, false)) {
            PsiJavaCodeReferenceElement classRef = factory.createClassReferenceElement(aClass);
            targetClass.getImplementsList().add(classRef);
          }
        }
        else {
          newMember = (PsiMember)targetClass.add(member);
        }
      }

      if (newMember != null) {
        decodeRefs(newMember, targetClass);
        final JavaRefactoringListenerManager listenerManager = JavaRefactoringListenerManager.getInstance(newMember.getProject());
        ((JavaRefactoringListenerManagerImpl)listenerManager).fireMemberMoved(myClass, newMember);
      }
    }

  }

}

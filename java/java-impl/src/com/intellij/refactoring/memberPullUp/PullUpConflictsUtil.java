/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 15:40:16
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.util.*;

public class PullUpConflictsUtil {
  private PullUpConflictsUtil() {}

  public static MultiMap<PsiElement, String> checkConflicts(final MemberInfo[] infos,
                                        PsiClass subclass,
                                        PsiClass superClass,
                                        PsiPackage targetPackage,
                                        PsiDirectory targetDirectory,
                                        final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    final Set<PsiMember> movedMembers = new HashSet<PsiMember>();
    final Set<PsiMethod> abstractMethods = new HashSet<PsiMethod>();
    final boolean isInterfaceTarget;
    final PsiElement targetRepresentativeElement;
    if (superClass != null) {
      isInterfaceTarget = superClass.isInterface();
      targetRepresentativeElement = superClass;
    }
    else {
      isInterfaceTarget = false;
      targetRepresentativeElement = targetDirectory;
    }
    for (MemberInfo info : infos) {
      PsiMember member = info.getMember();
      if (member instanceof PsiMethod) {
        if (!info.isToAbstract() && !isInterfaceTarget) {
          movedMembers.add(member);
        }
        else {
          abstractMethods.add((PsiMethod)member);
        }
      }
      else {
        movedMembers.add(member);
      }
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(movedMembers, superClass, conflicts, null);
    if (superClass != null) {
      checkSuperclassMembers(superClass, infos, conflicts);
      if (isInterfaceTarget) {
        checkInterfaceTarget(infos, conflicts);
      }
    }
    // check if moved methods use other members in the classes between Subclass and Superclass
    List<PsiElement> checkModuleConflictsList = new ArrayList<PsiElement>();
    for (PsiMember member : movedMembers) {
      if (member instanceof PsiMethod || member instanceof PsiClass) {
        ConflictingUsagesOfSubClassMembers visitor =
          new ConflictingUsagesOfSubClassMembers(member, movedMembers, abstractMethods, subclass, superClass,
                                                 superClass != null ? null : targetPackage, conflicts,
                                                 interfaceContainmentVerifier);
        member.accept(visitor);
      }
      checkModuleConflictsList.add(member);
    }
    for (final PsiMethod method : abstractMethods) {
      checkModuleConflictsList.add(method.getParameterList());
      checkModuleConflictsList.add(method.getReturnTypeElement());
      checkModuleConflictsList.add(method.getTypeParameterList());
    }
    RefactoringConflictsUtil.analyzeModuleConflicts(subclass.getProject(), checkModuleConflictsList,
                                           new UsageInfo[0], targetRepresentativeElement, conflicts);
    return conflicts;
  }

  private static void checkInterfaceTarget(MemberInfo[] infos, MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfo info : infos) {
      PsiElement member = info.getMember();

      if (member instanceof PsiField || member instanceof PsiClass) {

        if (!((PsiModifierListOwner)member).hasModifierProperty(PsiModifier.STATIC)
            && !(member instanceof PsiClass && ((PsiClass)member).isInterface())) {
          String message =
            RefactoringBundle.message("0.is.not.static.it.cannot.be.moved.to.the.interface", RefactoringUIUtil.getDescription(member, false));
          message = CommonRefactoringUtil.capitalize(message);
          conflictsList.putValue(member, message);
        }
      }

      if (member instanceof PsiField && ((PsiField)member).getInitializer() == null) {
        String message = RefactoringBundle.message("0.is.not.initialized.in.declaration.such.fields.are.not.allowed.in.interfaces",
                                                   RefactoringUIUtil.getDescription(member, false));
        conflictsList.putValue(member, CommonRefactoringUtil.capitalize(message));
      }
    }
  }

  private static void checkSuperclassMembers(PsiClass superClass,
                                             MemberInfo[] infos,
                                             MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfo info : infos) {
      PsiMember member = info.getMember();
      boolean isConflict = false;
      if (member instanceof PsiField) {
        String name = member.getName();

        isConflict = superClass.findFieldByName(name, false) != null;
      }
      else if (member instanceof PsiMethod) {
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, member.getContainingClass(), PsiSubstitutor.EMPTY);
        MethodSignature signature = ((PsiMethod) member).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(superClass, signature, false);
        isConflict = superClassMethod != null;
      }

      if (isConflict) {
        String message = RefactoringBundle.message("0.already.contains.a.1",
                                                   RefactoringUIUtil.getDescription(superClass, false),
                                                   RefactoringUIUtil.getDescription(member, false));
        message = CommonRefactoringUtil.capitalize(message);
        conflictsList.putValue(superClass, message);
      }
    }

  }

  private static class ConflictingUsagesOfSubClassMembers extends ClassMemberReferencesVisitor {
    private final PsiElement myScope;
    private final Set<PsiMember> myMovedMembers;
    private final Set<PsiMethod> myAbstractMethods;
    private final PsiClass mySubclass;
    private final PsiClass mySuperClass;
    private final PsiPackage myTargetPackage;
    private final MultiMap<PsiElement, String> myConflictsList;
    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

    ConflictingUsagesOfSubClassMembers(PsiElement scope,
                                       Set<PsiMember> movedMembers, Set<PsiMethod> abstractMethods,
                                       PsiClass subclass, PsiClass superClass,
                                       PsiPackage targetPackage, MultiMap<PsiElement, String> conflictsList,
                                       InterfaceContainmentVerifier interfaceContainmentVerifier) {
      super(subclass);
      myScope = scope;
      myMovedMembers = movedMembers;
      myAbstractMethods = abstractMethods;
      mySubclass = subclass;
      mySuperClass = superClass;
      myTargetPackage = targetPackage;
      myConflictsList = conflictsList;
      myInterfaceContainmentVerifier = interfaceContainmentVerifier;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember,
                                                    PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember != null
          && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
        if (classMember.hasModifierProperty(PsiModifier.STATIC)
            && !willBeMoved(classMember)) {
          final boolean isAccessible;
          if (mySuperClass != null) {
            isAccessible = PsiUtil.isAccessible(classMember, mySuperClass, null);
          }
          else if (myTargetPackage != null) {
            isAccessible = PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage);
          }
          else {
            isAccessible = classMember.hasModifierProperty(PsiModifier.PUBLIC);
          }
          if (!isAccessible) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.accessible.from.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = CommonRefactoringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);

          }
          return;
        }
        if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember)) {
          if (!existsInSuperClass(classMember)) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.moved.to.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = CommonRefactoringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);
          }
        }
      }
    }

    private boolean willBeMoved(PsiElement element) {
      PsiElement parent = element;
      while (parent != null) {
        if (myMovedMembers.contains(parent)) return true;
        parent = parent.getParent();
      }
      return false;
    }

    private boolean existsInSuperClass(PsiElement classMember) {
      if (!(classMember instanceof PsiMethod)) return false;
      final PsiMethod method = ((PsiMethod)classMember);
      if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) return true;
      if (mySuperClass == null) return false;
      final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
      return methodBySignature != null;
    }
  }


}

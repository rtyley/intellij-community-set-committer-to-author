/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ImportUtils{

    private ImportUtils(){
    }

    public static void addImportIfNeeded(PsiJavaFile file, PsiClass aClass) {
        final PsiFile containingFile = aClass.getContainingFile();
        if (file.equals(containingFile)) {
            return;
        }
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        final PsiImportList importList = file.getImportList();
        if (importList == null) {
            return;
        }
        final String containingPackageName = file.getPackageName();
        @NonNls final String packageName =
                ClassUtil.extractPackageName(qualifiedName);
        if (containingPackageName.equals(packageName) ||
                importList.findSingleClassImportStatement(qualifiedName) !=
                        null) {
            return;
        }
        if (importList.findOnDemandImportStatement(packageName) != null &&
                !hasDefaultImportConflict(qualifiedName, file) &&
                !hasOnDemandImportConflict(qualifiedName, file)) {
            return;
        }
        final Project project = importList.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory elementFactory =
                psiFacade.getElementFactory();
        final PsiImportStatement importStatement =
                elementFactory.createImportStatement(aClass);
        importList.add(importStatement);
    }

    public static boolean nameCanBeStaticallyImported(
            @NotNull String fqName, @NotNull String memberName,
            @NotNull PsiElement context) {
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(context, PsiClass.class);
        if (containingClass == null) {
            return false;
        }
        if (ClassUtils.isSubclass(containingClass, fqName)) {
            return true;
        }
        final PsiField field =
                containingClass.findFieldByName(memberName, true);
        if (field != null) {
            return false;
        }
        final PsiMethod[] methods =
                containingClass.findMethodsByName(memberName, true);
        if (methods.length > 0) {
            return false;
        }
        if (hasOnDemandImportStaticConflict(fqName, memberName, context,
                true)) {
            return false;
        }
        if (hasExactImportStaticConflict(fqName, memberName, context)) {
            return false;
        }
        return true;
    }

    public static boolean nameCanBeImported(@NotNull String fqName,
                                            @NotNull PsiElement context){
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(context, PsiClass.class);
        if (containingClass != null) {
            if (fqName.equals(containingClass.getQualifiedName())) {
                return true;
            }
            final String shortName = ClassUtil.extractClassName(fqName);
            final PsiClass[] innerClasses =
                    containingClass.getAllInnerClasses();
            for (PsiClass innerClass : innerClasses) {
                if (innerClass.hasModifierProperty(PsiModifier.PRIVATE)) {
                    continue;
                }
                if (innerClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                    if (!ClassUtils.inSamePackage(innerClass,
                            containingClass)) {
                        continue;
                    }
                }
                final String className = innerClass.getName();
                if (shortName.equals(className)) {
                    return false;
                }
            }
        }
        final PsiJavaFile file =
                PsiTreeUtil.getParentOfType(context, PsiJavaFile.class);
        if (file == null) {
            return false;
        }
        if(hasExactImportConflict(fqName, file)){
            return false;
        }
        if(hasOnDemandImportConflict(fqName, file, true)){
            return false;
        }
        if(containsConflictingClass(fqName, file)){
            return false;
        }
        return !containsConflictingClassName(fqName, file);
    }

    private static boolean containsConflictingClassName(String fqName,
                                                        PsiJavaFile file){
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final PsiClass[] classes = file.getClasses();
        for(PsiClass aClass : classes){
            if(shortName.equals(aClass.getName())){
                return true;
            }
        }
        return false;
    }

    private static boolean hasExactImportConflict(String fqName,
                                                  PsiJavaFile file){
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements =
                imports.getImportStatements();
        final int lastDotIndex = fqName.lastIndexOf((int) '.');
        final String shortName = fqName.substring(lastDotIndex + 1);
        final String dottedShortName = '.' + shortName;
        for(final PsiImportStatement importStatement : importStatements){
            if (importStatement.isOnDemand()) {
                continue;
            }
            final String importName = importStatement.getQualifiedName();
            if (importName ==  null){
                return false;
            }
            if(!importName.equals(fqName)){
                if(importName.endsWith(dottedShortName)){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasExactImportStaticConflict(
            String qualifierClass, String memberName, PsiElement context) {
        final PsiFile file = context.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        final PsiJavaFile javaFile = (PsiJavaFile) file;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }
        final PsiImportStaticStatement[] importStaticStatements =
                importList.getImportStaticStatements();
        for (PsiImportStaticStatement importStaticStatement :
                importStaticStatements) {
            if (importStaticStatement.isOnDemand()) {
                continue;
            }
            final String name = importStaticStatement.getReferenceName();
            if (!memberName.equals(name)) {
                continue;
            }
            final PsiJavaCodeReferenceElement importReference =
                    importStaticStatement.getImportReference();
            if (importReference == null) {
                continue;
            }
            final PsiElement qualifier = importReference.getQualifier();
            if (qualifier == null) {
                continue;
            }
            final String qualifierText = qualifier.getText();
            if (!qualifierClass.equals(qualifierText)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasOnDemandImportConflict(@NotNull String fqName,
                                                    @NotNull PsiJavaFile file){
        return hasOnDemandImportConflict(fqName, file, false);
    }

    /**
     * @param strict  if strict is true this method checks if the conflicting
     * class which is imported is actually used in the file. If it isn't the
     * on demand import can be overridden with an exact import for the fqName
     * without breaking stuff.
     */
    private static boolean hasOnDemandImportConflict(@NotNull String fqName,
                                                     @NotNull PsiJavaFile file,
                                                     boolean strict) {
        final PsiImportList imports = file.getImportList();
        if(imports == null){
            return false;
        }
        final PsiImportStatement[] importStatements =
                imports.getImportStatements();
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        for(final PsiImportStatement importStatement : importStatements){
            if (!importStatement.isOnDemand()) {
                continue;
            }
            final PsiJavaCodeReferenceElement importReference =
                    importStatement.getImportReference();
            if(importReference == null){
                continue;
            }
            final String packageText = importReference.getText();
            if(packageText.equals(packageName)){
                continue;
            }
            final PsiElement element = importReference.resolve();
            if (element == null || !(element instanceof PsiPackage)) {
                continue;
            }
            final PsiPackage aPackage = (PsiPackage) element;
            final PsiClass[] classes = aPackage.getClasses();
            for(final PsiClass aClass : classes){
                final String className = aClass.getName();
                if (!shortName.equals(className)) {
                    continue;
                }
                if (!strict) {
                    return true;
                }
                final String qualifiedClassName = aClass.getQualifiedName();
                final ClassReferenceVisitor visitor =
                        new ClassReferenceVisitor(qualifiedClassName);
                file.accept(visitor);
                return visitor.isReferenceFound();
            }
        }
        return hasJavaLangImportConflict(fqName, file);
    }

    public static boolean hasOnDemandImportStaticConflict(
            String fqName, String memberName, PsiElement context) {
        return hasOnDemandImportStaticConflict(fqName, memberName, context,
                false);
    }

    private static boolean hasOnDemandImportStaticConflict(
            String fqName, String memberName, PsiElement context,
            boolean strict) {
        final PsiFile file = context.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        final PsiJavaFile javaFile = (PsiJavaFile) file;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }
        final PsiImportStaticStatement[] importStaticStatements =
                importList.getImportStaticStatements();
        for (PsiImportStaticStatement importStaticStatement :
                importStaticStatements) {
            if (!importStaticStatement.isOnDemand()) {
                continue;
            }
            final PsiClass targetClass =
                    importStaticStatement.resolveTargetClass();
            if (targetClass == null) {
                continue;
            }
            final String name = targetClass.getQualifiedName();
            if (fqName.equals(name)) {
                continue;
            }
            final PsiField field = targetClass.findFieldByName(memberName, true);
            if (field != null) {
                if (!strict || memberReferenced(field, javaFile)) {
                    return true;
                }
            }
            final PsiMethod[] methods =
                    targetClass.findMethodsByName(memberName, true);
            if (methods.length > 0) {
                if (!strict || membersReferenced(methods, javaFile)) {
                    return true;
                }
            }

        }
        return false;
    }

    public static boolean hasDefaultImportConflict(String fqName,
                                                   PsiJavaFile file) {
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        final String filePackageName = file.getPackageName();
        if (filePackageName.equals(packageName)) {
            return false;
        }
        final Project project = file.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiPackage filePackage =
                psiFacade.findPackage(filePackageName);
        if (filePackage == null) {
            return false;
        }
        final PsiClass[] classes = filePackage.getClasses();
        for (PsiClass aClass : classes) {
            final String className = aClass.getName();
            if(shortName.equals(className)){
                return true;
            }
        }
        return false;
    }

    public static boolean hasJavaLangImportConflict(String fqName,
                                                    PsiJavaFile file) {
        final String shortName = ClassUtil.extractClassName(fqName);
        final String packageName = ClassUtil.extractPackageName(fqName);
        if (HardcodedMethodConstants.JAVA_LANG.equals(packageName)) {
            return false;
        }
        final Project project = file.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiPackage javaLangPackage =
                psiFacade.findPackage(HardcodedMethodConstants.JAVA_LANG);
        if(javaLangPackage == null){
            return false;
        }
        final PsiClass[] classes = javaLangPackage.getClasses();
        for(final PsiClass aClass : classes){
            final String className = aClass.getName();
            if(shortName.equals(className)){
                return true;
            }
        }
        return false;
    }

    private static boolean containsConflictingClass(String fqName,
                                                    PsiJavaFile file){
        final PsiClass[] classes = file.getClasses();
        for(PsiClass aClass : classes){
            if (containsConflictingInnerClass(fqName, aClass)) {
                return true;
            }
        }
        //return false;
        final ClassReferenceVisitor visitor =
                new ClassReferenceVisitor(fqName);
        file.accept(visitor);
        return visitor.isReferenceFound();
    }

    /**
     * ImportUtils currently checks all inner classes, even those that are
     * contained in inner classes themselves, because it doesn't know the
     * location of the original fully qualified reference. It should really only
     * check if the containing class of the fully qualified reference has any
     * conflicting inner classes.
     */
    private static boolean containsConflictingInnerClass(String fqName,
                                                         PsiClass aClass){
        final String shortName = ClassUtil.extractClassName(fqName);
        if(shortName.equals(aClass.getName())){
            if(!fqName.equals(aClass.getQualifiedName())){
                return true;
            }
        }
        final PsiClass[] classes = aClass.getInnerClasses();
        for (PsiClass innerClass : classes) {
            if (containsConflictingInnerClass(fqName, innerClass)) {
                return true;
            }
        }
        return false;
    }

    public static void addStaticImport(
            @NotNull String qualifierClass, @NotNull String memberName,
            @NotNull PsiElement context)
            throws IncorrectOperationException {
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(context, PsiClass.class);
        if (ClassUtils.isSubclass(containingClass, qualifierClass)) {
            return;
        }
        final PsiFile psiFile = context.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }
        final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        final PsiImportStatementBase existingImportStatement =
                importList.findSingleImportStatement(memberName);
        if (existingImportStatement != null) {
            return;
        } else {
            final PsiImportStaticStatement onDemandImportStatement =
                    findOnDemandImportStaticStatement(importList,
                            qualifierClass);
            if (onDemandImportStatement != null) {
                if (!hasOnDemandImportStaticConflict(qualifierClass,
                        memberName, context)) {
                    return;
                }
            }
        }
        final Project project = context.getProject();
        final GlobalSearchScope scope = context.getResolveScope();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiClass aClass = psiFacade.findClass(qualifierClass, scope);
        if (aClass == null) {
            return;
        }
        final String qualifiedName  = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        final List<PsiImportStaticStatement> imports =
                getMatchingImports(importList, qualifiedName);
        final CodeStyleSettings codeStyleSettings =
                CodeStyleSettingsManager.getSettings(project);
        final PsiElementFactory elementFactory = psiFacade.getElementFactory();
        if (imports.size() <
                codeStyleSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            importList.add(elementFactory.createImportStaticStatement(aClass,
                    memberName));
        } else {
            for (PsiImportStaticStatement importStatement : imports) {
                importStatement.delete();
            }
            importList.add(
                    elementFactory.createImportStaticStatement(aClass, "*"));
        }
    }

    private static PsiImportStaticStatement findOnDemandImportStaticStatement(
            PsiImportList importList, String qualifierClass) {
        final PsiImportStaticStatement[] importStaticStatements =
                importList.getImportStaticStatements();
        for (PsiImportStaticStatement importStaticStatement :
                importStaticStatements) {
            if (!importStaticStatement.isOnDemand()) {
                continue;
            }
            final PsiJavaCodeReferenceElement importReference =
                    importStaticStatement.getImportReference();
            if (importReference ==  null) {
                continue;
            }
            final String text = importReference.getText();
            if (qualifierClass.equals(text)) {
                return importStaticStatement;
            }
        }
        return null;
    }

    private static List<PsiImportStaticStatement> getMatchingImports(
            @NotNull PsiImportList importList, @NotNull String className){
        final List<PsiImportStaticStatement> imports = new ArrayList();
        for (PsiImportStaticStatement staticStatement :
                importList.getImportStaticStatements()) {
            final PsiClass psiClass = staticStatement.resolveTargetClass();
            if (psiClass == null) {
                continue;
            }
            if (!className.equals(psiClass.getQualifiedName())) {
                continue;
            }
            imports.add(staticStatement);
        }
        return imports;
    }

    public static boolean isStaticallyImported(@NotNull PsiMember member,
                                               @NotNull PsiElement context) {
        final PsiClass memberClass = member.getContainingClass();
        if (memberClass == null) {
            return false;
        }
        final PsiClass containingClass =
                PsiTreeUtil.getParentOfType(context, PsiClass.class);
        if (InheritanceUtil.isCorrectDescendant(containingClass, memberClass,
                true)) {
            return true;
        }
        final PsiFile psiFile = context.getContainingFile();
        if (!(psiFile instanceof PsiJavaFile)) {
            return false;
        }
        final PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        final PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }
        final String memberName = member.getName();
        final PsiImportStatementBase existingImportStatement =
                importList.findSingleImportStatement(memberName);
        if (existingImportStatement != null &&
                existingImportStatement instanceof PsiImportStaticStatement) {
            return true;
        }
        final String memberClassName = memberClass.getQualifiedName();
        final PsiImportStaticStatement onDemandImportStatement =
                findOnDemandImportStaticStatement(importList,
                        memberClassName);
        if (onDemandImportStatement != null) {
            if (!hasOnDemandImportStaticConflict(memberClassName,
                    memberName, context)) {
                return true;
            }
        }
        return false;
    }

    private static boolean memberReferenced(PsiField field,
                                            PsiJavaFile javaFile) {
        final MemberReferenceVisitor visitor =
                new MemberReferenceVisitor(field);
        javaFile.accept(visitor);
        return visitor.isReferenceFound();
    }

    private static boolean membersReferenced(PsiMethod[] methods,
                                             PsiJavaFile javaFile) {
        final MemberReferenceVisitor visitor =
                new MemberReferenceVisitor(methods);
        javaFile.accept(visitor);
        return visitor.isReferenceFound();
    }

    private static class MemberReferenceVisitor
            extends JavaRecursiveElementVisitor {

        private final PsiMember[] members;
        private boolean referenceFound = false;

        public MemberReferenceVisitor(PsiField field) {
            members = new PsiMember[]{field};
        }

        public MemberReferenceVisitor(PsiMethod[] methods) {
            members = methods;
        }

        @Override
        public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            if (referenceFound) {
                return;
            }
            super.visitReferenceElement(reference);
            if (reference.isQualified()) {
                return;
            }
            final PsiElement target = reference.resolve();
            for (PsiMember member : members) {
                if (member.equals(target)) {
                    referenceFound = true;
                    return;
                }
            }
        }

        public boolean isReferenceFound() {
            return referenceFound;
        }
    }

    private static class ClassReferenceVisitor
            extends JavaRecursiveElementVisitor{

        private final String name;
        private final String fullyQualifiedName;
        private boolean referenceFound = false;

        private ClassReferenceVisitor(String fullyQualifiedName) {
            name = ClassUtil.extractClassName(fullyQualifiedName);
            this.fullyQualifiedName = fullyQualifiedName;
        }

        @Override public void visitReferenceElement(
                PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            if (referenceFound) {
                return;
            }
            final String text = reference.getText();
            if (text.indexOf((int)'.') >= 0 || !name.equals(text)) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)
                    || element instanceof PsiTypeParameter) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            final String testClassName = aClass.getName();
            final String testClassQualifiedName = aClass.getQualifiedName();
            if (testClassQualifiedName == null || testClassName == null
                    || testClassQualifiedName.equals(fullyQualifiedName) ||
                    !testClassName.equals(name)) {
                return;
            }
            referenceFound = true;
        }

        public boolean isReferenceFound(){
            return referenceFound;
        }
    }
}
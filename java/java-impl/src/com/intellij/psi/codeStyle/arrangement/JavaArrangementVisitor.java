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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaArrangementVisitor extends JavaElementVisitor {
  
  private static final String NULL_CONTENT = "no content";
  
  private static final Map<String, ArrangementModifier> MODIFIERS = new HashMap<String, ArrangementModifier>();
  static {
    MODIFIERS.put(PsiModifier.PUBLIC, ArrangementModifier.PUBLIC);
    MODIFIERS.put(PsiModifier.PROTECTED, ArrangementModifier.PROTECTED);
    MODIFIERS.put(PsiModifier.PRIVATE, ArrangementModifier.PRIVATE);
    MODIFIERS.put(PsiModifier.PACKAGE_LOCAL, ArrangementModifier.PACKAGE_PRIVATE);
    MODIFIERS.put(PsiModifier.STATIC, ArrangementModifier.STATIC);
    MODIFIERS.put(PsiModifier.FINAL, ArrangementModifier.FINAL);
    MODIFIERS.put(PsiModifier.TRANSIENT, ArrangementModifier.TRANSIENT);
    MODIFIERS.put(PsiModifier.VOLATILE, ArrangementModifier.VOLATILE);
    MODIFIERS.put(PsiModifier.SYNCHRONIZED, ArrangementModifier.SYNCHRONIZED);
    MODIFIERS.put(PsiModifier.ABSTRACT, ArrangementModifier.ABSTRACT);
  }

  @NotNull private final Stack<JavaElementArrangementEntry>           myStack   = new Stack<JavaElementArrangementEntry>();
  @NotNull private final Map<PsiElement, JavaElementArrangementEntry> myEntries = new HashMap<PsiElement, JavaElementArrangementEntry>();

  @NotNull private final  JavaArrangementParseInfo     myInfo;
  @NotNull private final  Collection<TextRange>        myRanges;
  @NotNull private final  Set<ArrangementGroupingType> myGroupingRules;
  @Nullable private final Document                     myDocument;

  public JavaArrangementVisitor(@NotNull JavaArrangementParseInfo infoHolder,
                                @Nullable Document document,
                                @NotNull Collection<TextRange> ranges,
                                @NotNull Set<ArrangementGroupingType> groupingRules)
  {
    myInfo = infoHolder;
    myDocument = document;
    myRanges = ranges;
    myGroupingRules = groupingRules;
  }

  @Override
  public void visitClass(PsiClass aClass) {
    ArrangementEntryType type = ArrangementEntryType.CLASS;
    if (aClass.isEnum()) {
      type = ArrangementEntryType.ENUM;
    }
    else if (aClass.isInterface()) {
      type = ArrangementEntryType.INTERFACE;
    }
    JavaElementArrangementEntry entry = createNewEntry(aClass, type, aClass.getName(), true);
    processEntry(entry, aClass, aClass);
  }

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    JavaElementArrangementEntry entry = createNewEntry(aClass, ArrangementEntryType.CLASS, aClass.getName(), false);
    processEntry(entry, null, aClass);
  }

  @Override
  public void visitJavaFile(PsiJavaFile file) {
    for (PsiClass psiClass : file.getClasses()) {
      visitClass(psiClass);
    }
  }

  @Override
  public void visitField(PsiField field) {
    // There is a possible case that more than one field is declared at the same line like 'int i, j;'. We want to process only
    // the first one then.
    for (PsiElement e = field.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      if (e instanceof PsiWhiteSpace) {
        continue;
      }
      if (e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.COMMA) {
        return;
      }
      else {
        break;
      }
    }

    JavaElementArrangementEntry entry = createNewEntry(field, ArrangementEntryType.FIELD, field.getName(), true);
    processEntry(entry, field, field.getInitializer());
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    JavaElementArrangementEntry entry = createNewEntry(initializer, ArrangementEntryType.FIELD, null, true);
    if (entry == null) {
      return;
    }

    PsiElement classLBrace = null;
    PsiClass clazz = initializer.getContainingClass();
    if (clazz != null) {
      classLBrace = clazz.getLBrace();
    }
    for (PsiElement e = initializer.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      JavaElementArrangementEntry prevEntry;
      if (e == classLBrace) {
        prevEntry = myEntries.get(clazz);
      }
      else {
        prevEntry = myEntries.get(e);
      }
      if (prevEntry != null) {
        entry.addDependency(prevEntry);
      }
      if (!(e instanceof PsiWhiteSpace)) {
        break;
      }
    }
  }

  @Override
  public void visitMethod(PsiMethod method) {
    ArrangementEntryType type = method.isConstructor() ? ArrangementEntryType.CONSTRUCTOR : ArrangementEntryType.METHOD;
    JavaElementArrangementEntry entry = createNewEntry(method, type, method.getName(), true);
    if (entry == null) {
      return;
    }
    
    processEntry(entry, method, method.getBody());
    parseProperties(method, entry);
  }

  private void parseProperties(PsiMethod method, JavaElementArrangementEntry entry) {
    if (!myGroupingRules.contains(ArrangementGroupingType.GETTERS_AND_SETTERS)) {
      return;
    }

    String propertyName = null;
    boolean getter = true;
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      propertyName = PropertyUtil.getPropertyNameByGetter(method);
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      propertyName = PropertyUtil.getPropertyNameBySetter(method);
      getter = false;
    }

    if (propertyName == null) {
      return;
    }

    PsiClass containingClass = method.getContainingClass();
    String className = null;
    if (containingClass != null) {
      className = containingClass.getQualifiedName();
    }
    if (className == null) {
      className = NULL_CONTENT;
    }

    if (getter) {
      myInfo.registerGetter(propertyName, className, entry);
    }
    else {
      myInfo.registerSetter(propertyName, className, entry);
    }
  }

  @Override
  public void visitExpressionStatement(PsiExpressionStatement statement) {
    statement.getExpression().acceptChildren(this);
  }

  @Override
  public void visitNewExpression(PsiNewExpression expression) {
    PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
    if (anonymousClass == null) {
      return;
    }
    JavaElementArrangementEntry entry =
      createNewEntry(anonymousClass, ArrangementEntryType.CLASS, anonymousClass.getName(), false);
    processEntry(entry, null, anonymousClass);
  }

  @Override
  public void visitExpressionList(PsiExpressionList list) {
    for (PsiExpression expression : list.getExpressions()) {
      expression.acceptChildren(this);
    }
  }

  @Override
  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    for (PsiElement element : statement.getDeclaredElements()) {
      element.acceptChildren(this);
    }
  }

  private void processEntry(@Nullable JavaElementArrangementEntry entry,
                            @Nullable PsiModifierListOwner modifier,
                            @Nullable PsiElement nextPsiRoot)
  {
    if (entry == null) {
      return;
    }
    if (modifier != null) {
      parseModifiers(modifier.getModifierList(), entry);
    }
    if (nextPsiRoot == null) {
      return;
    }
    myStack.push(entry);
    try {
      nextPsiRoot.acceptChildren(this);
    }
    finally {
      myStack.pop();
    }
  }
  
  @Nullable
  private JavaElementArrangementEntry createNewEntry(@NotNull PsiElement element,
                                                     @NotNull ArrangementEntryType type,
                                                     @Nullable String name,
                                                     boolean canArrange)
  {
    TextRange range = element.getTextRange();
    if (!isWithinBounds(range)) {
      return null;
    }
    DefaultArrangementEntry current = getCurrent();
    JavaElementArrangementEntry entry;
    if (canArrange) {
      TextRange expandedRange = myDocument == null ? null : ArrangementUtil.expandToLine(range, myDocument);
      TextRange rangeToUse = expandedRange == null ? range : expandedRange;
      entry = new JavaElementArrangementEntry(current, rangeToUse, type, name, myDocument == null || expandedRange != null);
    }
    else {
      entry = new JavaElementArrangementEntry(current, range, type, name, false);
    }
    myEntries.put(element, entry);
    if (current == null) {
      myInfo.addEntry(entry);
    }
    else {
      current.addChild(entry);
    }
    
    return entry;
  }

  private boolean isWithinBounds(@NotNull TextRange range) {
    for (TextRange textRange : myRanges) {
      if (textRange.intersects(range)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private DefaultArrangementEntry getCurrent() {
    return myStack.isEmpty() ? null : myStack.peek();
  }

  @SuppressWarnings("MagicConstant")
  private static void parseModifiers(@Nullable PsiModifierList modifierList, @NotNull JavaElementArrangementEntry entry) {
    if (modifierList == null) {
      return;
    }
    for (String modifier : PsiModifier.MODIFIERS) {
      if (modifierList.hasModifierProperty(modifier)) {
        ArrangementModifier arrangementModifier = MODIFIERS.get(modifier);
        if (arrangementModifier != null) {
          entry.addModifier(arrangementModifier);
        }
      }
    }
    if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
      entry.addModifier(ArrangementModifier.PACKAGE_PRIVATE);
    }
  }
}
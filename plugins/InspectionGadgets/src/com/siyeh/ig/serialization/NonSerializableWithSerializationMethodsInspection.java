/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiAnonymousClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableWithSerializationMethodsInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "NonSerializableClassWithSerializationMethods";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.serializable.class.with.readwriteobject.display.name");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos[2] instanceof PsiAnonymousClass) {
      return null;
    }
    return new MakeSerializableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean hasReadObject = ((Boolean)infos[0]).booleanValue();
    final boolean hasWriteObject = ((Boolean)infos[1]).booleanValue();
    final PsiClass aClass = (PsiClass)infos[2];
    if (aClass instanceof PsiAnonymousClass) {
      if (hasReadObject && hasWriteObject) {
        return InspectionGadgetsBundle.message(
          "non.serializable.anonymous.with.readwriteobject.problem.descriptor.both");
      }
      else if (hasWriteObject) {
        return InspectionGadgetsBundle.message(
          "non.serializable.anonymous.with.readwriteobject.problem.descriptor.write");
      }
      else {
        return InspectionGadgetsBundle.message(
          "non.serializable.anonymous.with.readwriteobject.problem.descriptor.read");
      }
    }
    else {
      if (hasReadObject && hasWriteObject) {
        return InspectionGadgetsBundle.message(
          "non.serializable.class.with.readwriteobject.problem.descriptor.both");
      }
      else if (hasWriteObject) {
        return InspectionGadgetsBundle.message(
          "non.serializable.class.with.readwriteobject.problem.descriptor.write");
      }
      else {
        return InspectionGadgetsBundle.message(
          "non.serializable.class.with.readwriteobject.problem.descriptor.read");
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonserializableDefinesSerializationMethodsVisitor();
  }

  private static class NonserializableDefinesSerializationMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      final boolean hasReadObject =
        SerializationUtils.hasReadObject(aClass);
      final boolean hasWriteObject =
        SerializationUtils.hasWriteObject(aClass);
      if (!hasWriteObject && !hasReadObject) {
        return;
      }
      if (SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerClassError(aClass, Boolean.valueOf(hasReadObject),
                         Boolean.valueOf(hasWriteObject), aClass);
    }
  }
}
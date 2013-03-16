/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;

/**
 * @author peter
 */
public class LoggingContributor extends AstTransformContributor {
  private static final ImmutableMap<String, String> ourLoggers = ImmutableMap.<String, String>builder().
    put("groovy.util.logging.Log", "java.util.logging.Logger").
    put("groovy.util.logging.Commons", "org.apache.commons.logging.Log").
    put("groovy.util.logging.Log4j", "org.apache.log4j.Logger").
    put("groovy.util.logging.Slf4j", "org.slf4j.Logger").
    build();

  @Override
  public void collectFields(@NotNull GrTypeDefinition psiClass, @NotNull Collection<GrField> collector) {
    GrModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return;

    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      String qname = annotation.getQualifiedName();
      String logger = ourLoggers.get(qname);
      if (logger != null) {
        String fieldName = PsiUtil.getAnnoAttributeValue(annotation, "value", "log");
        GrLightField field = new GrLightField(psiClass, fieldName, logger);
        field.setNavigationElement(annotation);
        field.getModifierList().setModifiers(PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC);
        field.setOriginInfo("created by @" + annotation.getShortName());
        collector.add(field);
      }
    }
  }
}

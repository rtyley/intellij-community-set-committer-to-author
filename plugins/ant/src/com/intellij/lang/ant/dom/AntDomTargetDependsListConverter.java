/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ant.AntSupport;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.converters.DelimitedListConverter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomTargetDependsListConverter extends DelimitedListConverter<AntDomTarget> {
  public AntDomTargetDependsListConverter() {
    super(",\t ");
  }

  @Nullable
  protected AntDomTarget convertString(@Nullable String string, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element == null) {
      return null;
    }
    final AntDomProject project = element.getAntProject();
    return project.findTarget(string);
  }

  protected String toString(@Nullable AntDomTarget antDomTarget) {
    if (antDomTarget != null) {
      final GenericAttributeValue<String> name = antDomTarget.getName();
      if (name != null) {
        return name.getStringValue();
      }
    }
    return null;
  }

  protected Object[] getReferenceVariants(ConvertContext context, GenericDomValue<List<AntDomTarget>> existingDeps) {
    final Set<String> existingTargetNames = StringSetSpinAllocator.alloc();
    try {
      final AntDomElement antDom = AntSupport.getInvocationAntDomElement(context);
      if (antDom == null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
      final AntDomProject project = antDom.getAntProject();
      if (existingDeps != null) {
        final List<AntDomTarget> domTargetList = existingDeps.getValue();
        if (domTargetList != null && domTargetList.size() > 0) {
          for (AntDomTarget target : domTargetList) {
            existingTargetNames.add(target.getName().getStringValue());
          }
        }
      }
      final List<Object> result = new ArrayList<Object>();
      final List<AntDomTarget> allTargets = project.getDeclaredTargets();
      for (AntDomTarget target : allTargets) {
        final String targetName = target.getName().getStringValue();
        if (targetName != null && !existingTargetNames.contains(targetName)) {
          result.add(LookupElementBuilder.create(target, targetName));
        }
      }
      return result.toArray(new Object[result.size()]);
    }
    finally {
      StringSetSpinAllocator.dispose(existingTargetNames);
    }
  }

  protected PsiElement resolveReference(@Nullable AntDomTarget antDomTarget, ConvertContext context) {
    return antDomTarget != null? antDomTarget.getXmlElement() : null;
  }

  protected String getUnresolvedMessage(String value) {
    return CodeInsightBundle.message("error.cannot.resolve.default.message", value);
  }

}

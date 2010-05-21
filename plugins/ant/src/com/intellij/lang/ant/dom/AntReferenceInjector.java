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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReferenceInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene Zhuravlev
*         Date: Apr 9, 2010
*/
class AntReferenceInjector implements DomReferenceInjector {
  public String resolveString(@Nullable String unresolvedText, @NotNull ConvertContext context) {
    // todo: speed optimization: disable string resolution in places where it is not applicable
    if (unresolvedText == null) {
      return null;
    }
    final DomElement element = context.getInvocationElement();
    return AntStringResolver.computeString(element, unresolvedText);
  }

  @NotNull
  public PsiReference[] inject(@Nullable String unresolvedText, @NotNull PsiElement element, @NotNull ConvertContext context) {
    if (element instanceof XmlAttributeValue) {
      final List<PsiReference> refs = new ArrayList<PsiReference>();
      addPropertyReferences(context, (XmlAttributeValue)element, refs);
      return refs.toArray(new PsiReference[refs.size()]);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static void addPropertyReferences(@NotNull ConvertContext context, final XmlAttributeValue xmlAttributeValue, final List<PsiReference> result) {
    final String value = xmlAttributeValue.getValue();
    final DomElement contextElement = context.getInvocationElement();
    if (xmlAttributeValue != null && value.indexOf("@{") < 0) {
      final int valueBeginingOffset = Math.abs(xmlAttributeValue.getTextRange().getStartOffset() - xmlAttributeValue.getValueTextRange().getStartOffset());
      int startIndex;
      int endIndex = -1;
      while ((startIndex = value.indexOf("${", endIndex + 1)) > endIndex) {
        if (startIndex > 0 && value.charAt(startIndex - 1) == '$') {
          // the '$' is escaped
          endIndex = startIndex + 1;
          continue;
        }
        startIndex += 2;
        endIndex = startIndex;
        int nestedBrackets = 0;
        while (value.length() > endIndex) {
          final char ch = value.charAt(endIndex);
          if (ch == '}') {
            if (nestedBrackets == 0) {
              break;
            }
            --nestedBrackets;
          }
          else if (ch == '{') {
            ++nestedBrackets;
          }
          ++endIndex;
        }
        if (nestedBrackets > 0 || endIndex > value.length()) return;
        if (endIndex >= startIndex) {
          final String propName = value.substring(startIndex, endIndex);
          //if (antFile.isEnvironmentProperty(propName) && antFile.getProperty(propName) == null) {
          //  continue;
          //}

          result.add(new AntDomPropertyReference(
            contextElement, xmlAttributeValue, propName, new TextRange(valueBeginingOffset + startIndex, valueBeginingOffset + endIndex))
          );
        }
        endIndex = startIndex;
      }
    }
  }

}

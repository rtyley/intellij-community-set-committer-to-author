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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlContentDFA;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionProvider {

  public void complete(CompletionParameters parameters, final CompletionResultSet result, PsiElement element) {
    if (!XmlCompletionContributor.isXmlNameCompletion(parameters)) {
      return;
    }
    result.stopHere();
    if (!(element.getParent() instanceof XmlTag)) {
      return;
    }

    final XmlTag tag = (XmlTag)element.getParent();
    final XmlTag parentTag = tag.getParentTag();
    final XmlContentDFA dfa = XmlContentDFA.getContentDFA(parentTag);
    if (dfa == null) return;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (XmlTag subTag : parentTag.getSubTags()) {
          if (subTag == tag) {
            break;
          }
          dfa.transition(subTag);
        }
        List<XmlElementDescriptor> elements = dfa.getPossibleElements();
        for (XmlElementDescriptor elementDescriptor: elements) {
          addElementToResult(elementDescriptor, result);
        }
      }
    });
  }

  private static void addElementToResult(@NotNull XmlElementDescriptor descriptor, CompletionResultSet result) {
    LookupElementBuilder builder = createLookupElement(descriptor);
    result.addElement(builder.setInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        XmlTagInsertHandler.INSTANCE.handleInsert(context, item);
      }
    }));
  }

  public static LookupElementBuilder createLookupElement(@NotNull XmlElementDescriptor descriptor) {
    LookupElementBuilder builder = LookupElementBuilder.create(descriptor.getName());
    if (descriptor instanceof XmlElementDescriptorImpl) {
      builder = builder.setTypeText(((XmlElementDescriptorImpl)descriptor).getNamespace(), true);
    }
    return builder;
  }

}

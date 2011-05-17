/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject.xml;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.*;
import com.intellij.util.PairProcessor;
import com.intellij.util.PatternValuesIndex;
import gnu.trove.THashMap;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.AbstractTagInjection;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This is the main part of the injection code. The component registers a language injector, the reference provider that
 * supplies completions for language-IDs and regular expression enum-values as well as the Quick Edit action.
 * <p/>
 * The injector obtains the static injection configuration for each XML tag, attribute or String literal and also
 * dynamically computes the prefix/suffix for the language fragment from binary expressions.
 * <p/>
 * It also tries to deal with the "glued token" problem by removing or adding whitespace to the prefix/suffix.
 */
public final class XmlLanguageInjector implements MultiHostInjector {

  private final Configuration myConfiguration;
  private volatile Trinity<Long, Pattern, Collection<String>> myXmlIndex;
  private final LanguageInjectionSupport mySupport;

  public XmlLanguageInjector(Configuration configuration) {
    myConfiguration = configuration;
    mySupport = InjectorUtils.findNotNullInjectionSupport(LanguageInjectionSupport.XML_SUPPORT_ID);
  }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(XmlTag.class, XmlAttribute.class);
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement host) {
    final XmlElement xmlElement = (XmlElement) host;
    if (notInIndex(xmlElement)) return;
    final TreeSet<TextRange> ranges = new TreeSet<TextRange>(InjectorUtils.RANGE_COMPARATOR);
    final PsiFile containingFile = xmlElement.getContainingFile();
    getInjectedLanguage(xmlElement, new PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>>() {
      public boolean process(final Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
          if (ranges.contains(trinity.third.shiftRight(trinity.first.getTextRange().getStartOffset()))) return true;
        }
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : list) {
          final PsiLanguageInjectionHost host = trinity.first;
          if (host.getContainingFile() != containingFile) continue;
          final TextRange textRange = trinity.third;
          ranges.add(textRange.shiftRight(host.getTextRange().getStartOffset()));
        }
        InjectorUtils.registerInjection(language, list, containingFile, registrar);
        InjectorUtils.registerSupport(mySupport, true, registrar);
        return true;
      }
    });
  }

  void getInjectedLanguage(final PsiElement place, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor) {
    if (place instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)place;
      //getXmlAnnotatedElementsValue().contains(xmlTag.getLocalName());
      for (final BaseInjection injection : myConfiguration.getInjections(LanguageInjectionSupport.XML_SUPPORT_ID)) {
        if (injection.acceptsPsiElement(xmlTag)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final Ref<Boolean> hasSubTags = Ref.create(Boolean.FALSE);
          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();

          xmlTag.acceptChildren(new PsiElementVisitor() {
            @Override
            public void visitElement(final PsiElement element) {
              if (element instanceof XmlText) {
                if (element.getTextLength() == 0) return;
                final List<TextRange> list = injection.getInjectedArea(element);
                final InjectedLanguage l = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
                for (TextRange textRange : list) {
                  result.add(Trinity.create((PsiLanguageInjectionHost)element, l, textRange));
                }
              }
              else if (element instanceof XmlTag) {
                hasSubTags.set(Boolean.TRUE);
                if (injection instanceof AbstractTagInjection && ((AbstractTagInjection)injection).isApplyToSubTagTexts()) {
                  element.acceptChildren(this);
                }
              }
            }
          });
          if (!result.isEmpty()) {
            if (separateFiles) {
              for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
                processor.process(language, Collections.singletonList(trinity));
              }
            }
            else {
              for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
                trinity.first.putUserData(LanguageInjectionSupport.HAS_UNPARSABLE_FRAGMENTS, hasSubTags.get());
              }
              processor.process(language, result);
            }
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
    else if (place instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute)place;
      final XmlAttributeValue value = attribute.getValueElement();
      if (value == null) return;
      // Check that we don't inject anything into embedded (e.g. JavaScript) content:
      // XmlToken: "
      // JSEmbeddedContent
      // XmlToken "

      // Actually IDEA shouldn't ask for injected languages at all in this case.
      final PsiElement[] children = value.getChildren();
      if (children.length < 3 || !(children[1] instanceof XmlToken) ||
          ((XmlToken)children[1]).getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        return;
      }

      for (BaseInjection injection : myConfiguration.getInjections(LanguageInjectionSupport.XML_SUPPORT_ID)) {
        if (injection.acceptsPsiElement(attribute)) {
          final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
          if (language == null) continue;
          final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

          final List<TextRange> ranges = injection.getInjectedArea(value);
          if (ranges.isEmpty()) continue;
          final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
          final InjectedLanguage l = InjectedLanguage.create(injection.getInjectedLanguageId(), injection.getPrefix(), injection.getSuffix(), false);
          for (TextRange textRange : ranges) {
            result.add(Trinity.create((PsiLanguageInjectionHost)value, l, textRange));
          }
          if (separateFiles) {
            for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
              processor.process(language, Collections.singletonList(trinity));
            }
          }
          else {
            processor.process(language, result);
          }
          if (injection.isTerminal()) {
            break;
          }
        }
      }
    }
  }

  // NOTE: local name of an xml entity or attribute value should match at least one string in the index 
  private boolean notInIndex(XmlElement xmlElement) {
    final Trinity<Long, Pattern, Collection<String>> index = getXmlAnnotatedElementsValue();
    final XmlTag tag;
    if (xmlElement instanceof XmlAttribute) {
      final XmlAttribute attribute = (XmlAttribute)xmlElement;
      if (areThereInjectionsWithText(attribute.getLocalName(), index)) return false;
      if (areThereInjectionsWithText(attribute.getValue(), index)) return false;
      //if (areThereInjectionsWithText(attribute.getNamespace(), index)) return false;
      tag = attribute.getParent();
    }
    else tag = (XmlTag)xmlElement;
    if (areThereInjectionsWithText(tag.getLocalName(), index)) return false;
    //if (areThereInjectionsWithText(tag.getNamespace(), index)) return false;
    return true;
  }


  private static boolean areThereInjectionsWithText(final String text, Trinity<Long, Pattern, Collection<String>> index) {
    if (text == null) return false;
    if (index.third.contains(text)) return true;
    if (index.second.matcher(text).matches()) return true;
    return false;
  }

  private Trinity<Long, Pattern, Collection<String>> getXmlAnnotatedElementsValue() {
    Trinity<Long, Pattern, Collection<String>> index = myXmlIndex;
    if (index == null || myConfiguration.getModificationCount() != index.first.longValue()) {
      final Map<ElementPattern<?>, BaseInjection> map = new THashMap<ElementPattern<?>, BaseInjection>();
      for (BaseInjection injection : myConfiguration.getInjections(XmlLanguageInjectionSupport.XML_SUPPORT_ID)) {
        for (InjectionPlace place : injection.getInjectionPlaces()) {
          if (!place.isEnabled() || place.getElementPattern() == null) continue;
          map.put(place.getElementPattern(), injection);
        }
      }
      final Collection<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
      index = Trinity.create(myConfiguration.getModificationCount(), buildPattern(stringSet), stringSet);
      myXmlIndex = index;
    }
    return index;
  }

  private static Pattern buildPattern(Collection<String> stringSet) {
    final StringBuilder sb = new StringBuilder();
    for (String s : stringSet) {
      if (!InjectorUtils.isRegexp(s)) continue;
      if (sb.length() > 0) sb.append('|');
      sb.append("(?:").append(s).append(")");
    }
    return Pattern.compile(sb.toString());
  }

}

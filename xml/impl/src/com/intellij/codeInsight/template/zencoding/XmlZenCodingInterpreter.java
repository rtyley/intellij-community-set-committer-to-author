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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateInvokationListener;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
class XmlZenCodingInterpreter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.zencoding.XmlZenCodingInterpreter");
  private static final String ATTRS = "ATTRS";

  private final List<Token> myTokens;
  private final CustomTemplateCallback myCallback;
  private final TemplateInvokationListener myListener;
  private static final String NUMBER_IN_ITERATION_PLACE_HOLDER = "$";
  private State myState;

  XmlZenCodingInterpreter(List<Token> tokens, CustomTemplateCallback callback, State initialState, TemplateInvokationListener listener) {
    myTokens = tokens;
    myCallback = callback;
    myListener = listener;
    myState = initialState;
  }

  private void finish(boolean inSeparateEvent) {
    myCallback.gotoEndOffset();
    if (myListener != null) {
      myListener.finished(inSeparateEvent);
    }
  }

  private void gotoChild(Object templateBoundsKey) {
    int startOfTemplate = myCallback.getStartOfTemplate(templateBoundsKey);
    int endOfTemplate = myCallback.getEndOfTemplate(templateBoundsKey);
    Editor editor = myCallback.getEditor();
    int offset = myCallback.getOffset();

    PsiFile file = myCallback.getFile();

    PsiElement element = file.findElementAt(offset);
    if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
      return;
    }

    int newOffset = -1;
    XmlTag tag = PsiTreeUtil.findElementOfClassAtRange(file, startOfTemplate, endOfTemplate, XmlTag.class);
    if (tag != null) {
      for (PsiElement child : tag.getChildren()) {
        if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_END_TAG_START) {
          newOffset = child.getTextOffset();
        }
      }
    }

    if (newOffset >= 0) {
      myCallback.fixEndOffset();
      editor.getCaretModel().moveToOffset(newOffset);
    }

    /*CharSequence tagName = getPrecedingTagName(text, offset, startOfTemplate);
    if (tagName != null) {
      *//*if (!hasClosingTag(text, tagName, offset, endOfTemplate)) {
        document.insertString(offset, "</" + tagName + '>');
      }*//*
    }
    else if (offset != endOfTemplate) {
      tagName = getPrecedingTagName(text, endOfTemplate, startOfTemplate);
      if (tagName != null) {
        *//*fixEndOffset();
        document.insertString(endOfTemplate, "</" + tagName + '>');*//*
        editor.getCaretModel().moveToOffset(endOfTemplate);
      }
    }*/
  }

  public boolean invoke(int startIndex) {
    final int n = myTokens.size();
    TemplateToken templateToken = null;
    int number = -1;
    for (int i = startIndex; i < n; i++) {
      final int finalI = i;
      Token token = myTokens.get(i);
      switch (myState) {
        case OPERATION:
          if (templateToken != null) {
            if (token instanceof MarkerToken || token instanceof OperationToken) {
              final char sign = token instanceof OperationToken ? ((OperationToken)token).mySign : XmlZenCodingTemplate.MARKER;
              if (sign == XmlZenCodingTemplate.MARKER || sign == '+') {
                final Object key = new Object();
                myCallback.fixStartOfTemplate(key);
                TemplateInvokationListener listener = new TemplateInvokationListener() {
                  public void finished(boolean inSeparateEvent) {
                    myState = State.WORD;
                    if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
                      myCallback.fixEndOffset();
                    }
                    if (sign == '+') {
                      myCallback.gotoEndOfTemplate(key);
                    }
                    if (inSeparateEvent) {
                      invoke(finalI + 1);
                    }
                  }
                };
                if (!invokeTemplate(templateToken, myCallback, listener, 0)) {
                  return false;
                }
                templateToken = null;
              }
              else if (sign == '>') {
                if (!startTemplateAndGotoChild(templateToken, finalI)) {
                  return false;
                }
                templateToken = null;
              }
              else if (sign == '*') {
                myState = State.NUMBER;
              }
            }
            else {
              fail();
            }
          }
          break;
        case WORD:
          if (token instanceof TemplateToken) {
            templateToken = ((TemplateToken)token);
            myState = State.OPERATION;
          }
          else {
            fail();
          }
          break;
        case NUMBER:
          if (token instanceof NumberToken) {
            number = ((NumberToken)token).myNumber;
            myState = State.AFTER_NUMBER;
          }
          else {
            fail();
          }
          break;
        case AFTER_NUMBER:
          if (token instanceof MarkerToken || token instanceof OperationToken) {
            char sign = token instanceof OperationToken ? ((OperationToken)token).mySign : XmlZenCodingTemplate.MARKER;
            if (sign == XmlZenCodingTemplate.MARKER || sign == '+') {
              if (!invokeTemplateSeveralTimes(templateToken, 0, number, finalI)) {
                return false;
              }
              templateToken = null;
            }
            else if (number > 1) {
              return invokeTemplateAndProcessTail(templateToken, 0, number, i + 1);
            }
            else {
              assert number == 1;
              if (!startTemplateAndGotoChild(templateToken, finalI)) {
                return false;
              }
              templateToken = null;
            }
            myState = State.WORD;
          }
          else {
            fail();
          }
          break;
      }
    }
    finish(startIndex == n);
    return true;
  }

  private boolean startTemplateAndGotoChild(TemplateToken templateToken, final int index) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    TemplateInvokationListener listener = new TemplateInvokationListener() {
      public void finished(boolean inSeparateEvent) {
        myState = State.WORD;
        gotoChild(key);
        if (inSeparateEvent) {
          invoke(index + 1);
        }
      }
    };
    if (!invokeTemplate(templateToken, myCallback, listener, 0)) {
      return false;
    }
    return true;
  }

  private boolean invokeTemplateSeveralTimes(final TemplateToken templateToken,
                                             final int startIndex,
                                             final int count,
                                             final int globalIndex) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      final int finalI = i;
      TemplateInvokationListener listener = new TemplateInvokationListener() {
        public void finished(boolean inSeparateEvent) {
          myState = State.WORD;
          if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
            myCallback.fixEndOffset();
          }
          myCallback.gotoEndOfTemplate(key);
          if (inSeparateEvent) {
            if (finalI + 1 < count) {
              invokeTemplateSeveralTimes(templateToken, finalI + 1, count, globalIndex);
            }
            else {
              invoke(globalIndex + 1);
            }
          }
        }
      };
      if (!invokeTemplate(templateToken, myCallback, listener, i)) {
        return false;
      }
    }
    return true;
  }

  private boolean invokeTemplateAndProcessTail(final TemplateToken templateToken,
                                               final int startIndex,
                                               final int count,
                                               final int tailStart) {
    final Object key = new Object();
    myCallback.fixStartOfTemplate(key);
    for (int i = startIndex; i < count; i++) {
      final int finalI = i;
      final boolean[] flag = new boolean[]{false};
      TemplateInvokationListener listener = new TemplateInvokationListener() {
        public void finished(boolean inSeparateEvent) {
          gotoChild(key);
          XmlZenCodingInterpreter interpreter =
            new XmlZenCodingInterpreter(myTokens, myCallback, State.WORD, new TemplateInvokationListener() {
              public void finished(boolean inSeparateEvent) {
                if (myCallback.getOffset() != myCallback.getEndOfTemplate(key)) {
                  myCallback.fixEndOffset();
                }
                myCallback.gotoEndOfTemplate(key);
                if (inSeparateEvent) {
                  invokeTemplateAndProcessTail(templateToken, finalI + 1, count, tailStart);
                }
              }
            });
          if (interpreter.invoke(tailStart)) {
            if (inSeparateEvent) {
              invokeTemplateAndProcessTail(templateToken, finalI + 1, count, tailStart);
            }
          }
          else {
            flag[0] = true;
          }
        }
      };
      if (!invokeTemplate(templateToken, myCallback, listener, i) || flag[0]) {
        return false;
      }
    }
    finish(count == 0);
    return true;
  }

  private static boolean containsAttrsVar(TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  private static void removeVariablesWhichHasNoSegment(TemplateImpl template) {
    Set<String> segments = new HashSet<String>();
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      segments.add(template.getSegmentName(i));
    }
    IntArrayList varsToRemove = new IntArrayList();
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (!segments.contains(varName)) {
        varsToRemove.add(i);
      }
    }
    for (int i = 0; i < varsToRemove.size(); i++) {
      template.removeVariable(varsToRemove.get(i));
    }
  }

  @Nullable
  private static Map<String, String> buildPredefinedValues(List<Pair<String, String>> attribute2value, int numberInIteration) {
    StringBuilder result = new StringBuilder();
    for (Iterator<Pair<String, String>> it = attribute2value.iterator(); it.hasNext();) {
      Pair<String, String> pair = it.next();
      String name = pair.first;
      String value = getValue(pair, numberInIteration);
      result.append(name).append("=\"").append(value).append('"');
      if (it.hasNext()) {
        result.append(' ');
      }
    }
    String attributes = result.toString();
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ATTRS, attributes);
    }
    return predefinedValues;
  }

  private static String getValue(Pair<String, String> pair, int numberInIteration) {
    return pair.second.replace(NUMBER_IN_ITERATION_PLACE_HOLDER, Integer.toString(numberInIteration + 1));
  }

  @Nullable
  private static String addAttrsVar(TemplateImpl modifiedTemplate, XmlTag tag) {
    String text = tag.getContainingFile().getText();
    PsiElement[] children = tag.getChildren();
    if (children.length >= 1 &&
        children[0] instanceof XmlToken &&
        ((XmlToken)children[0]).getTokenType() == XmlTokenType.XML_START_TAG_START) {
      PsiElement beforeAttrs = children[0];
      if (children.length >= 2 && children[1] instanceof XmlToken && ((XmlToken)children[1]).getTokenType() == XmlTokenType.XML_NAME) {
        beforeAttrs = children[1];
      }
      TextRange range = beforeAttrs.getTextRange();
      if (range == null) {
        return null;
      }
      int offset = range.getEndOffset();
      text = text.substring(0, offset) + " $ATTRS$" + text.substring(offset);
      modifiedTemplate.addVariable(ATTRS, "", "", false);
      return text;
    }
    return null;
  }

  private static boolean invokeTemplate(TemplateToken token,
                                        final CustomTemplateCallback callback,
                                        final TemplateInvokationListener listener,
                                        int numberInIteration) {
    List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(token.myAttribute2Value);
    if (callback.isLiveTemplateApplicable(token.myKey)) {
      if (token.myTemplate != null) {
        if (attr2value.size() > 0) {
          TemplateImpl modifiedTemplate = token.myTemplate.copy();
          XmlTag tag = XmlZenCodingTemplate.parseXmlTagInTemplate(token.myTemplate.getString(), callback.getProject());
          assert tag != null;
          for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
            Pair<String, String> pair = iterator.next();
            if (tag.getAttribute(pair.first) != null) {
              tag.setAttribute(pair.first, getValue(pair, numberInIteration));
              iterator.remove();
            }
          }
          String text = null;
          if (!containsAttrsVar(modifiedTemplate) && attr2value.size() > 0) {
            String textWithAttrs = addAttrsVar(modifiedTemplate, tag);
            if (textWithAttrs != null) {
              text = textWithAttrs;
            }
            else {
              for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
                Pair<String, String> pair = iterator.next();
                tag.setAttribute(pair.first, getValue(pair, numberInIteration));
                iterator.remove();
              }
            }
          }
          if (text == null) {
            text = tag.getContainingFile().getText();
          }
          modifiedTemplate.setString(text);
          removeVariablesWhichHasNoSegment(modifiedTemplate);
          Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
          return callback.startTemplate(modifiedTemplate, predefinedValues, listener);
        }
        else {
          return callback.startTemplate(token.myTemplate, null, listener);
        }
      }
      else {
        Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
        return callback.startTemplate(token.myKey, predefinedValues, listener);
      }
    }
    else {
      TemplateImpl template = new TemplateImpl("", "");
      template.addTextSegment('<' + token.myKey);
      if (attr2value.size() > 0) {
        template.addVariable(ATTRS, "", "", false);
        template.addVariableSegment(ATTRS);
      }
      template.addTextSegment(">");
      template.addVariableSegment(TemplateImpl.END);
      template.addTextSegment("</" + token.myKey + ">");
      template.setToReformat(true);
      Map<String, String> predefinedValues = buildPredefinedValues(attr2value, numberInIteration);
      return callback.startTemplate(template, predefinedValues, listener);
    }
  }

  private static void fail() {
    LOG.error("Input string was checked incorrectly during isApplicable() invokation");
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public abstract class LookupElementDecorator<T extends LookupElement> extends LookupElement {
  private final T myDelegate;

  protected LookupElementDecorator(T delegate) {
    myDelegate = delegate;
    myDelegate.copyUserDataTo(this);
    final PrefixMatcher matcher = delegate.getPrefixMatcher();
    if (matcher != PrefixMatcher.FALSE_MATCHER) {
      final boolean prefixStillMatches = CompletionService.getCompletionService().prefixMatches(this, matcher);
      assert prefixStillMatches;
    }
  }

  public T getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean setPrefixMatcher(@NotNull PrefixMatcher matcher) {
    myDelegate.setPrefixMatcher(matcher);
    return super.setPrefixMatcher(matcher);
  }

  @NotNull
  public String getLookupString() {
    return myDelegate.getLookupString();
  }

  @Override
  public Set<String> getAllLookupStrings() {
    return myDelegate.getAllLookupStrings();
  }

  @NotNull
  @Override
  public Object getObject() {
    return myDelegate.getObject();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myDelegate.handleInsert(context);
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    myDelegate.renderElement(presentation);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LookupElementDecorator that = (LookupElementDecorator)o;

    if (!myDelegate.equals(that.myDelegate)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myDelegate.hashCode();
  }

  @NotNull
  public static <T extends LookupElement> LookupElementDecorator<T> withInsertHandler(@NotNull T element, @NotNull final InsertHandler<? super LookupElementDecorator<T>> insertHandler) {
    return new InsertingDecorator<T>(element, insertHandler);
  }

  @NotNull
  public static <T extends LookupElement> LookupElementDecorator<T> withRenderer(@NotNull final T element, @NotNull final LookupElementRenderer<? super LookupElementDecorator<T>> visagiste) {
    return new VisagisteDecorator<T>(element, visagiste);
  }

  @Override
  public <T> T as(Class<T> aClass) {
    final T t = super.as(aClass);
    return t == null ? myDelegate.as(aClass) : t;
  }

  private static class InsertingDecorator<T extends LookupElement> extends LookupElementDecorator<T> {
    private final InsertHandler<? super LookupElementDecorator<T>> myInsertHandler;

    public InsertingDecorator(T element, InsertHandler<? super LookupElementDecorator<T>> insertHandler) {
      super(element);
      myInsertHandler = insertHandler;
    }

    @Override
    public void handleInsert(InsertionContext context) {
      myInsertHandler.handleInsert(context, this);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      InsertingDecorator that = (InsertingDecorator)o;

      if (!myInsertHandler.equals(that.myInsertHandler)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myInsertHandler.hashCode();
      return result;
    }
  }

  private static class VisagisteDecorator<T extends LookupElement> extends LookupElementDecorator<T> {
    private final LookupElementRenderer<? super LookupElementDecorator<T>> myVisagiste;

    public VisagisteDecorator(T element, LookupElementRenderer<? super LookupElementDecorator<T>> visagiste) {
      super(element);
      myVisagiste = visagiste;
    }

    @Override
    public void renderElement(final LookupElementPresentation presentation) {
      myVisagiste.renderElement(this, presentation);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      VisagisteDecorator that = (VisagisteDecorator)o;

      if (!myVisagiste.getClass().equals(that.myVisagiste.getClass())) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myVisagiste.getClass().hashCode();
      return result;
    }
  }
}

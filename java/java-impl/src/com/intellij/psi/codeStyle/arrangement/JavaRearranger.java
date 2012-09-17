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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementConditionsGrouper;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.*;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*;

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:31 PM
 */
public class JavaRearranger implements Rearranger<JavaElementArrangementEntry>, ArrangementStandardSettingsAware,
                                       ArrangementConditionsGrouper
{

  // Type
  @NotNull private static final Set<ArrangementEntryType> SUPPORTED_TYPES = EnumSet.of(INTERFACE, CLASS, ENUM, FIELD, METHOD, CONSTRUCTOR);

  // Modifier
  @NotNull private static final Set<ArrangementModifier> SUPPORTED_MODIFIERS = EnumSet.of(
    PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE, STATIC, FINAL, VOLATILE, TRANSIENT, SYNCHRONIZED, ABSTRACT
  );

  @NotNull private static final Object                                NO_TYPE           = new Object();
  @NotNull private static final Map<Object, Set<ArrangementModifier>> MODIFIERS_BY_TYPE = new HashMap<Object, Set<ArrangementModifier>>();
  @NotNull private static final Collection<Set<?>>                    MUTEXES           = new ArrayList<Set<?>>();

  static {
    EnumSet<ArrangementModifier> visibilityModifiers = EnumSet.of(PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE);
    MUTEXES.add(visibilityModifiers);
    MUTEXES.add(SUPPORTED_TYPES);

    Set<ArrangementModifier> commonModifiers = concat(visibilityModifiers, STATIC, FINAL);
    
    MODIFIERS_BY_TYPE.put(NO_TYPE, commonModifiers);
    MODIFIERS_BY_TYPE.put(ENUM, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(INTERFACE, visibilityModifiers);
    MODIFIERS_BY_TYPE.put(CLASS, concat(commonModifiers, ABSTRACT));
    MODIFIERS_BY_TYPE.put(METHOD, concat(commonModifiers, SYNCHRONIZED, ABSTRACT));
    MODIFIERS_BY_TYPE.put(CONSTRUCTOR, concat(commonModifiers, SYNCHRONIZED));
    MODIFIERS_BY_TYPE.put(FIELD, concat(commonModifiers, TRANSIENT, VOLATILE));
  }

  @NotNull private static final List<Set<ArrangementMatchCondition>> GROUPING_RULES = ContainerUtilRt.newArrayList();
  static {
    GROUPING_RULES.add(new HashSet<ArrangementMatchCondition>(
      ContainerUtil.map(
        SUPPORTED_TYPES,
        new Function<ArrangementEntryType, ArrangementMatchCondition>() {
          @Override
          public ArrangementMatchCondition fun(ArrangementEntryType type) {
            return new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, type);
          }
        }
      )
    ));
  }

  private static final List<StdArrangementRule> DEFAULT_RULES = new ArrayList<StdArrangementRule>();

  static {
    ArrangementModifier[] visibility = {PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE};
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, STATIC, FINAL, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, STATIC, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, FINAL, modifier);
    }
    for (ArrangementModifier modifier : visibility) {
      and(FIELD, modifier);
    }
    and(FIELD);
    and(CONSTRUCTOR);
    and(METHOD, STATIC);
    and(METHOD);
    and(ENUM);
    and(INTERFACE);
    and(CLASS, STATIC);
    and(CLASS);
  }

  private static void and(@NotNull Object... conditions) {
    if (conditions.length == 1) {
      DEFAULT_RULES.add(new StdArrangementRule(new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(
        ArrangementUtil.parseType(conditions[0]), conditions[0]
      ))));
      return;
    }

    ArrangementCompositeMatchCondition composite = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    for (Object condition : conditions) {
      composite.addOperand(new ArrangementAtomMatchCondition(ArrangementUtil.parseType(condition), condition));
    }
    DEFAULT_RULES.add(new StdArrangementRule(new StdArrangementEntryMatcher(composite)));
  }

  @NotNull
  private static Set<ArrangementModifier> concat(@NotNull Set<ArrangementModifier> base, ArrangementModifier... modifiers) {
    EnumSet<ArrangementModifier> result = EnumSet.copyOf(base);
    Collections.addAll(result, modifiers);
    return result;
  }

  @Nullable
  @Override
  public Pair<JavaElementArrangementEntry, List<JavaElementArrangementEntry>> parseWithNew(@NotNull PsiElement root,
                                                                                           @Nullable Document document,
                                                                                           @NotNull Collection<TextRange> ranges,
                                                                                           @NotNull PsiElement element)
  {
    List<JavaElementArrangementEntry> existingEntries = new ArrayList<JavaElementArrangementEntry>();
    root.accept(new JavaArrangementVisitor(existingEntries, document, ranges));

    List<JavaElementArrangementEntry> newEntry = new ArrayList<JavaElementArrangementEntry>();
    element.accept(new JavaArrangementVisitor(newEntry, document, Collections.singleton(element.getTextRange())));
    if (newEntry.size() != 1) {
      return null;
    }
    return Pair.create(newEntry.get(0), existingEntries);
  }

  @NotNull
  @Override
  public List<JavaElementArrangementEntry> parse(@NotNull PsiElement root,
                                                 @Nullable Document document,
                                                 @NotNull Collection<TextRange> ranges)
  {
    // Following entries are subject to arrangement: class, interface, field, method.
    List<JavaElementArrangementEntry> result = new ArrayList<JavaElementArrangementEntry>();
    root.accept(new JavaArrangementVisitor(result, document, ranges));
    return result;
  }

  @Override
  public int getBlankLines(@NotNull CodeStyleSettings settings,
                           @Nullable JavaElementArrangementEntry parent,
                           @Nullable JavaElementArrangementEntry previous,
                           @NotNull JavaElementArrangementEntry target)
  {
    if (previous == null) {
      return -1;
    }

    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    switch (target.getType()) {
      case FIELD:
        if (parent != null && parent.getType() == INTERFACE) {
          return commonSettings.BLANK_LINES_AROUND_FIELD_IN_INTERFACE;
        }
        else {
          return commonSettings.BLANK_LINES_AROUND_FIELD;
        }
      case METHOD:
        if (parent != null && parent.getType() == INTERFACE) {
          return commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE;
        }
        else {
          return commonSettings.BLANK_LINES_AROUND_METHOD;
        }
      default:
        return commonSettings.BLANK_LINES_AROUND_CLASS;
    }
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatchCondition current) {
    return SUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatchCondition current) {
    if (current == null) {
      return SUPPORTED_MODIFIERS.contains(modifier);
    }

    final Ref<Object> typeRef = new Ref<Object>();
    current.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition setting) {
        if (setting.getType() == ArrangementSettingType.TYPE) {
          typeRef.set(setting.getValue());
        }
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition setting) {
        for (ArrangementMatchCondition n : setting.getOperands()) {
          if (typeRef.get() != null) {
            return;
          }
          n.invite(this);
        }
      }
    });
    Object key = typeRef.get() == null ? NO_TYPE : typeRef.get();
    Set<ArrangementModifier> modifiers = MODIFIERS_BY_TYPE.get(key);
    return modifiers != null && modifiers.contains(modifier);
  }

  @NotNull
  @Override
  public Collection<Set<?>> getMutexes() {
    return MUTEXES;
  }

  @NotNull
  @Override
  public List<Set<ArrangementMatchCondition>> getGroupingConditions() {
    return GROUPING_RULES;
  }

  @Nullable
  @Override
  public List<StdArrangementRule> getDefaultRules() {
    return DEFAULT_RULES;
  }
}

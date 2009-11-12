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
package com.intellij.spellchecker.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.lang.*;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.quickfixes.AcceptWordAsCorrect;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class SpellCheckingInspection extends LocalInspectionTool {
  
  public static final String SPELL_CHECKING_INSPECTION_TOOL_NAME = "SpellCheckingInspection";
  private static final AcceptWordAsCorrect BATCH_ACCEPT_FIX = new AcceptWordAsCorrect();

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return SpellCheckerBundle.message("spelling");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return SpellCheckerBundle.message("spellchecking.inspection.name");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SPELL_CHECKING_INSPECTION_TOOL_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return SpellCheckerManager.getHighlightDisplayLevel();
  }

  private static final Map<Language, SpellcheckingStrategy> factories = new HashMap<Language, SpellcheckingStrategy>();

  private static void ensureFactoriesAreLoaded() {
    synchronized (factories) {
      if (!factories.isEmpty()) return;
      final SpellcheckingStrategy[] spellcheckingStrategies = Extensions.getExtensions(SpellcheckingStrategy.EP_NAME);
      if (spellcheckingStrategies != null) {
        for (SpellcheckingStrategy spellcheckingStrategy : spellcheckingStrategies) {
          final Language language = spellcheckingStrategy.getLanguage();
          if (language != Language.ANY) {
            factories.put(language, spellcheckingStrategy);
          }
        }
      }
    }
  }


  private static SpellcheckingStrategy getFactoryByLanguage(@NotNull Language lang) {
    return factories.containsKey(lang) ? factories.get(lang) : factories.get(PlainTextLanguage.INSTANCE);
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {

      @Override
      public void visitElement(final PsiElement element) {

        final ASTNode node = element.getNode();
        if (node == null) {
          return;
        }
        // Extract parser definition from element
        final Language language = element.getLanguage();
        final IElementType elementType = node.getElementType();
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

        // Handle selected options
        if (parserDefinition != null) {
          if (parserDefinition.getStringLiteralElements().contains(elementType)) {
            if (!processLiterals) {
              return;
            }
          }
          else if (parserDefinition.getCommentTokens().contains(elementType)) {
            if (!processComments) {
              return;
            }
          }
          else if (!processCode) {
            return;
          }
        }

        ensureFactoriesAreLoaded();

        final SpellcheckingStrategy factoryByLanguage = getFactoryByLanguage(language);
        final Tokenizer tokenizer = factoryByLanguage.getTokenizer(element);
        @SuppressWarnings({"unchecked"})
        final Token[] tokens = tokenizer.tokenize(element);
        if (tokens == null) {
          return;
        }
        for (Token token : tokens) {
          inspect(token, holder, isOnTheFly, getNamesValidators());
        }
      }
    };
  }

  private static void inspect(Token token, ProblemsHolder holder, boolean isOnTheFly, NamesValidator... validators) {
    List<CheckArea> areaList = TextSplitter.splitText(token.getText());
    if (areaList == null) {
      return;
    }
    for (CheckArea area : areaList) {
      boolean ignored = area.isIgnored();
      boolean keyword = isKeyword(validators, token.getElement(), area.getWord());
      if (!ignored && !keyword) {
        inspect(area, token, holder, isOnTheFly);
      }
    }
  }


  private static void inspect(@NotNull CheckArea area, @NotNull Token token, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    SpellCheckerManager manager = SpellCheckerManager.getInstance(token.getElement().getProject());

    final TextRange textRange = area.getTextRange();
    final String word = area.getWord();

    if (textRange == null || word == null) {
      return;
    }

    if (manager.hasProblem(word)) {
      List<SpellCheckerQuickFix> fixes = new ArrayList<SpellCheckerQuickFix>();
      if (isOnTheFly) {
        if (!token.isUseRename()) {
          fixes.add(new ChangeTo());
        }
        else {
          fixes.add(new RenameTo());
        }
      }

      final AcceptWordAsCorrect acceptWordAsCorrect = isOnTheFly ? BATCH_ACCEPT_FIX : new AcceptWordAsCorrect();
      fixes.add(acceptWordAsCorrect);

      final ProblemDescriptor problemDescriptor = createProblemDescriptor(token, holder, textRange, fixes, isOnTheFly);
      holder.registerProblem(problemDescriptor);
    }

  }

  private static ProblemDescriptor createProblemDescriptor(Token token,
                                                           ProblemsHolder holder,
                                                           TextRange textRange, Collection<SpellCheckerQuickFix> fixes, boolean onTheFly) {
    //TODO: these descriptions eat LOTS of HEAP on batch run - need either to make them constant or evaluate template dynamically
    //  ( add something like #text substitution)
    final String defaultDescription = SpellCheckerBundle.message("typo.in.word.ref");
    final String tokenDescription = token.getDescription();
    final String description = tokenDescription == null ? defaultDescription : tokenDescription;
    final TextRange highlightRange = TextRange.from(token.getOffset() + textRange.getStartOffset(), textRange.getLength());
    assert highlightRange.getStartOffset()>=0 : token.getText();
    final LocalQuickFix[] quickFixes = fixes.size() > 0 ? fixes.toArray(new LocalQuickFix[fixes.size()]) : null;

    final ProblemDescriptor problemDescriptor = holder.getManager()
      .createProblemDescriptor(token.getElement(), highlightRange, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, holder.isOnTheFly(),
                               quickFixes);
    if(onTheFly) {
      for (SpellCheckerQuickFix fix : fixes) {
        fix.setDescriptor(problemDescriptor);
      }
    }
    return problemDescriptor;
  }

  @Nullable
  public static NamesValidator[] getNamesValidators() {
    final Object[] extensions = Extensions.getExtensions("com.intellij.lang.namesValidator");
    NamesValidator[] validators = null;
    if (extensions != null) {
      List<NamesValidator> validatorList = new ArrayList<NamesValidator>();
      for (Object extension : extensions) {
        if (extension instanceof LanguageExtensionPoint && ((LanguageExtensionPoint)extension).getInstance() instanceof NamesValidator) {
          validatorList.add((NamesValidator)((LanguageExtensionPoint)extension).getInstance());
        }
      }
      if (validatorList.size() > 0) {
        validators = new NamesValidator[validatorList.size()];
        validatorList.toArray(validators);
      }
    }
    return validators;
  }

  private static boolean isKeyword(NamesValidator[] validators, PsiElement element, String word) {
    if (validators == null) {
      return false;
    }
    for (NamesValidator validator : validators) {
      if (validator.isKeyword(word, element.getProject())) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings({"PublicField"})
  public boolean processCode = true;
  public boolean processLiterals = true;
  public boolean processComments = true;

  @Override
  public JComponent createOptionsPanel() {
    final Box verticalBox = Box.createVerticalBox();
    verticalBox.add(new SingleCheckboxOptionsPanel(SpellCheckerBundle.message("process.code"), this, "processCode"));
    verticalBox.add(new SingleCheckboxOptionsPanel(SpellCheckerBundle.message("process.literals"), this, "processLiterals"));
    verticalBox.add(new SingleCheckboxOptionsPanel(SpellCheckerBundle.message("process.comments"), this, "processComments"));
    /*HyperlinkLabel linkToSettings = new HyperlinkLabel(SpellCheckerBundle.message("link.to.settings"));
    linkToSettings.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(final HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext());
          // ??project?

        }
      }
    });

    verticalBox.add(linkToSettings);*/
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(verticalBox, BorderLayout.NORTH);
    return panel;

  }
}

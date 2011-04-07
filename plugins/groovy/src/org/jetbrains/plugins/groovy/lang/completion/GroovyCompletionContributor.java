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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.*;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.util.containers.CollectionFactory.hashMap;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {
  private static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);

  private static final String[] THIS_SUPER = {"this", "super"};
  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_IMPORT_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      GroovyInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiMember member = item.getObject();
      final PsiClass containingClass = item.getContainingClass();
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil.
        findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);

      if (ref != null &&
          ref.getQualifier() == null &&
          !importAlreadyExists(member, ((GroovyFile)context.getFile()), ref) &&
          !PsiManager.getInstance(context.getProject()).areElementsEquivalent(ref.resolve(), member)) {
        ref.bindToElementViaStaticImport(containingClass);
      }

    }
  };

  private static boolean importAlreadyExists(final PsiMember member, final GroovyFile file, final PsiElement place) {
    final PsiManager manager = file.getManager();
    PsiScopeProcessor processor = new PsiScopeProcessor() {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        return !manager.areElementsEquivalent(element, member);
      }

      @Override
      public <T> T getHint(Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(Event event, Object associated) {
      }
    };

    boolean skipStaticImports = member instanceof PsiClass;
    final GrImportStatement[] imports = file.getImportStatements();
    final ResolveState initial = ResolveState.initial();
    for (GrImportStatement anImport : imports) {
      if (skipStaticImports == anImport.isStatic()) continue;
      if (!anImport.processDeclarations(processor, initial, null, place)) return true;
    }
    return false;
  }


  private static final InsertHandler<JavaGlobalMemberLookupElement> QUALIFIED_METHOD_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      GroovyInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiClass containingClass = item.getContainingClass();
      context.getDocument().insertString(context.getStartOffset(), containingClass.getName() + ".");
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil
        .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);
      if (ref != null) {
        ref.bindToElement(containingClass);
      }
    }
  };

  private static final PsiElementPattern.Capture<PsiElement> STATEMENT_START =
    psiElement(GroovyElementTypes.mIDENT).andOr(
      psiElement().afterLeaf(StandardPatterns.or(
        psiElement().isNull(),
        psiElement().withElementType(TokenSets.SEPARATORS),
        psiElement(GroovyElementTypes.mLCURLY),
        psiElement(GroovyElementTypes.kELSE)
      )).andNot(psiElement().withParent(GrTypeDefinitionBody.class))
        .andNot(psiElement(PsiErrorElement.class)),
      psiElement().afterLeaf(psiElement(GroovyTokenTypes.mRPAREN)).withSuperParent(2, StandardPatterns.or(
        psiElement(GrForStatement.class),
        psiElement(GrWhileStatement.class),
        psiElement(GrIfStatement.class)
      ))
    );

  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL =
    psiElement().afterLeaf(psiElement().withElementType(
      elementType().oneOf(GroovyElementTypes.mNUM_DOUBLE, GroovyElementTypes.mNUM_INT, GroovyElementTypes.mNUM_LONG, GroovyElementTypes.mNUM_FLOAT, GroovyElementTypes.mNUM_BIG_INT, GroovyElementTypes.mNUM_BIG_DECIMAL)));
  private static final ElementPattern<PsiElement> AFTER_AT = psiElement().afterLeaf("@");
  private static final ElementPattern<PsiElement> IN_CATCH_TYPE = psiElement().afterLeaf(psiElement().withText("(").withParent(GrCatchClause.class));


  private static void addAllClasses(CompletionParameters parameters, final CompletionResultSet result, final InheritorsHolder inheritors) {
    result.stopHere();
    final PsiElement position = parameters.getPosition();
    final ElementFilter filter = getClassFilter(position);
    AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher(), parameters.getInvocationCount() <= 1, new Consumer<PsiClass>() {
      @Override
      public void consume(PsiClass psiClass) {
        if (!inheritors.alreadyProcessed(psiClass) && filter.isAcceptable(psiClass, position)) {
          result.addElement(GroovyCompletionUtil.createClassLookupItem(psiClass));
        }
      }
    });
  }

  private static ElementFilter getClassFilter(PsiElement position) {
    if (AFTER_AT.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION);
    }
    if (IN_CATCH_TYPE.accepts(position)) {
      return new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE);
    }
    return TrueFilter.INSTANCE;
  }

  public GroovyCompletionContributor() {
    //provide 'this' and 'super' completions in ClassName.<caret>
    extend(CompletionType.BASIC, AFTER_DOT, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();

        assert position.getParent() instanceof GrReferenceExpression;
        final GrReferenceExpression refExpr = ((GrReferenceExpression)position.getParent());
        final GrExpression qualifier = refExpr.getQualifierExpression();
        if (!(qualifier instanceof GrReferenceExpression)) return;

        GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifier;
        final PsiElement resolved = referenceExpression.resolve();
        if (!(resolved instanceof PsiClass)) return;

        if (CompletionService.getCompletionService().getAdvertisementText() == null && parameters.getInvocationCount() > 0 &&
            CompletionUtil.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
          final String shortcut = getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION);
          if (shortcut != null) {
            CompletionService.getCompletionService().setAdvertisementText("Pressing " + shortcut + " without a class qualifier would show all accessible static methods");
          }
        }

        if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

        for (String keyword : THIS_SUPER) {
          result.addElement(LookupElementBuilder.create(keyword));
        }
      }
    });

    MapArgumentCompletionProvider.register(this);

    // class name stuff

    extend(CompletionType.CLASS_NAME, psiElement().withParent(GrReferenceElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (((GrReferenceElement)position.getParent()).getQualifier() != null) return;

        if (StringUtil.isEmpty(result.getPrefixMatcher().getPrefix())) return;

        completeStaticMembers(position).processStaticMethodsGlobally(result);
      }
    });


    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        addAllClasses(parameters, result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(parameters)), new InheritorsHolder(parameters.getPosition(), result));
      }
    });

    extend(CompletionType.BASIC, STATEMENT_START, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        result.addElement(LookupElementBuilder.create("if").setBold().setInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            TailTypes.IF_LPARENTH.processTail(context.getEditor(), context.getTailOffset());
          }
        }));
      }
    });

    extend(CompletionType.BASIC, psiElement(PsiElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        final PsiElement parent = position.getParent();
        if (parent instanceof GrReferenceElement) {
          if (parent.getParent() instanceof GrImportStatement && ((GrReferenceElement)parent).getQualifier() != null) {
            result.addElement(LookupElementBuilder.create("*"));
          }


          addKeywords(parameters, result);
          completeReference(parameters, result, (GrReferenceElement)parent);
        } else if (IN_CATCH_TYPE.accepts(position) || AFTER_AT.accepts(position)) {
          addAllClasses(parameters, result, new InheritorsHolder(position, result));
        } else if (GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(position, true)) {
          addDeclarationTypes(result, position);
        }
      }
    });

  }

  private static void addDeclarationTypes(CompletionResultSet result, PsiElement position) {
    ResolverProcessor processor = CompletionProcessor.createClassCompletionProcessor(position);
    ResolveUtil.treeWalkUp((GroovyPsiElement)position.getParent(), processor, false);
    for (Object variant : GroovyCompletionUtil.getCompletionVariants(processor.getCandidates())) {

      if (variant instanceof LookupElement) {
        result.addElement((LookupElement)variant);
        continue;
      }

      final String lookupString = variant.toString();
      if (lookupString == null) continue;

      LookupElementBuilder builder = LookupElementBuilder.create(variant, lookupString);
      if (variant instanceof Iconable) {
        builder = builder.setIcon(((Iconable)variant).getIcon(Iconable.ICON_FLAG_VISIBILITY));
      }
      result.addElement(builder.setInsertHandler(GroovyInsertHandler.INSTANCE));
    }
  }

  private static void addKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
    final GroovyCompletionData completionData = new GroovyCompletionData();
    completionData.addKeywordVariants(keywordVariants, position, parameters.getOriginalFile());
    completionData.completeKeywordsBySet(lookupSet, keywordVariants, position, result.getPrefixMatcher(), parameters.getOriginalFile());

    for (final LookupElement item : lookupSet) {
      result.addElement(item);
    }
  }


  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    if (AFTER_NUMBER_LITERAL.accepts(parameters.getPosition())) {
      result.stopHere();
      return;
    }

    super.fillCompletionVariants(parameters, result);
  }

  private static void completeReference(final CompletionParameters parameters, final CompletionResultSet result, GrReferenceElement reference) {
    final PsiElement position = parameters.getPosition();

    final InheritorsHolder inheritors = new InheritorsHolder(position, result);
    if (GroovySmartCompletionContributor.AFTER_NEW.accepts(position)) {
      GroovySmartCompletionContributor.generateInheritorVariants(parameters, result.getPrefixMatcher(), inheritors);
    }

    final int invocationCount = parameters.getInvocationCount();
    final boolean secondCompletionInvoked = invocationCount > 1;

    final String prefix = result.getPrefixMatcher().getPrefix();
    final boolean skipAccessors = !secondCompletionInvoked && !prefix.startsWith(GET_PREFIX) &&
                                  !prefix.startsWith(SET_PREFIX) &&
                                  !prefix.startsWith(IS_PREFIX);


    result.restartCompletionOnPrefixChange(GET_PREFIX);
    result.restartCompletionOnPrefixChange(SET_PREFIX);
    result.restartCompletionOnPrefixChange(IS_PREFIX);
    final Map<PsiModifierListOwner, LookupElement> staticMembers = hashMap();
    final PsiElement qualifier = reference.getQualifier();
    final PsiType qualifierType = qualifier instanceof GrExpression ? ((GrExpression)qualifier).getType() : null;

    final ElementFilter classFilter = getClassFilter(position);

    reference.processVariants(new Consumer<Object>() {
      public void consume(Object element) {
        if (element instanceof PsiClass && inheritors.alreadyProcessed((PsiClass)element)) {
          return;
        }
        if (element instanceof LookupElement && inheritors.alreadyProcessed((LookupElement)element)) {
          return;
        }

        final LookupElement lookupElement = element instanceof PsiClass
                                            ? GroovyCompletionUtil.createClassLookupItem((PsiClass)element)
                                            : GroovyCompletionUtil.getLookupElement(element);
        Object object = lookupElement.getObject();
        PsiSubstitutor substitutor = null;
        GroovyResolveResult resolveResult = null;
        if (object instanceof GroovyResolveResult) {
          resolveResult = (GroovyResolveResult)object;
          substitutor = resolveResult.getSubstitutor();
          object = ((GroovyResolveResult)object).getElement();
        }

        final boolean autopopup = parameters.getInvocationCount() == 0;
        //skip default groovy methods
        if (!secondCompletionInvoked &&
            object instanceof GrGdkMethod &&
            GroovyCompletionUtil.skipDefGroovyMethod((GrGdkMethod)object, substitutor, qualifierType)) {
          if (!autopopup) {
            showInfo();
          }
          return;
        }

        //skip operator methods
        if (!secondCompletionInvoked &&
            object instanceof PsiMethod &&
            GroovyCompletionUtil.OPERATOR_METHOD_NAMES.contains(((PsiMethod)object).getName())) {
          if (!checkForIterator((PsiMethod)object)) {
            if (!autopopup) {
              showInfo();
            }
            return;
          }
        }

        //skip accessors if there is no get, set, is prefix
        if (skipAccessors && object instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)object)) {
          if (!autopopup) {
            showInfo();
          }
          return;
        }

        //skip inaccessible elements
        if (!secondCompletionInvoked && resolveResult != null && !resolveResult.isAccessible()) {
          if (!autopopup) {
            showInfo();
          }
          return;
        }

        if ((object instanceof PsiMethod || object instanceof PsiField) &&
            ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
          if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
            staticMembers.put((PsiModifierListOwner)object, lookupElement);
            return;
          }
        }
        if (object instanceof PsiClass && !classFilter.isAcceptable(object, position)) {
          return;
        }
        result.addElement(JavaCompletionUtil.highlightIfNeeded(qualifierType, lookupElement, object));
      }
    });

    if (qualifier == null) {
      completeStaticMembers(position).processMembersOfRegisteredClasses(null, new PairConsumer<PsiMember, PsiClass>() {
        @Override
        public void consume(PsiMember member, PsiClass psiClass) {
          if (member instanceof GrAccessorMethod) {
            member = ((GrAccessorMethod)member).getProperty();
          }
          final String name = member.getName();
          if (name == null || !result.getPrefixMatcher().prefixMatches(name)) {
            staticMembers.remove(member);
            return;
          }
          staticMembers.put(member, createGlobalMemberElement(member, psiClass, true));

        }
      });

      GroovySmartCompletionContributor.addExpectedClassMembers(parameters, result);


      if (StringUtil.isCapitalized(result.getPrefixMatcher().getPrefix()) || parameters.relaxMatching()) {
        addAllClasses(parameters, result, inheritors);
      }
    }
    result.addAllElements(staticMembers.values());
  }

  private static boolean checkForIterator(PsiMethod method) {
    if (!"next".equals(method.getName())) return false;

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    final PsiClass iterator = JavaPsiFacade.getInstance(method.getProject()).findClass(CommonClassNames.JAVA_UTIL_ITERATOR,
                                                                                       method.getResolveScope());
    return InheritanceUtil.isInheritorOrSelf(containingClass, iterator, true);
  }

  private static void showInfo() {
    if (StringUtil.isEmpty(CompletionService.getCompletionService().getAdvertisementText())) {
      CompletionService.getCompletionService()
        .setAdvertisementText(GroovyBundle.message("invoke.completion.second.time.to.show.skipped.methods"));
    }
  }

  private static StaticMemberProcessor completeStaticMembers(PsiElement position) {
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport) {
        return createGlobalMemberElement(member, containingClass, shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        return new JavaGlobalMemberLookupElement(overloads, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                                 shouldImport);
      }

      @Override
      protected boolean isAccessible(PsiMember member) {
        boolean result = super.isAccessible(member);

        if (!result && member instanceof GrField) {
          GrAccessorMethod[] getters = ((GrField)member).getGetters();
          return getters.length > 0 && super.isAccessible(getters[0]);
        }

        return result;
      }
    };
    final PsiFile file = position.getContainingFile();
    if (file instanceof GroovyFile) {
      for (GrImportStatement statement : ((GroovyFile)file).getImportStatements()) {
        if (statement.isStatic()) {
          GrCodeReferenceElement importReference = statement.getImportReference();
          if (importReference != null) {
            if (!statement.isOnDemand()) {
              importReference = importReference.getQualifier();
            }
            if (importReference != null) {
              final PsiElement target = importReference.resolve();
              if (target instanceof PsiClass) {
                processor.importMembersOf((PsiClass)target);
              }
            }
          }
        }
      }
    }
    return processor;
  }

  static JavaGlobalMemberLookupElement createGlobalMemberElement(PsiMember member, PsiClass containingClass, boolean shouldImport) {
    return new JavaGlobalMemberLookupElement(member, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                             shouldImport);
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    if (context.getCompletionType() == CompletionType.BASIC && context.getFile() instanceof GroovyFile) {
      if (semicolonNeeded(context)) {
        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + ";");
      }
      else if (isInPossibleClosureParameter(context.getFile().findElementAt(context.getStartOffset()))) {
        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + "->");
      }
    }
  }

  public static boolean isInPossibleClosureParameter(PsiElement position) { //Closure cl={String x, <caret>...
    if (position == null) return false;

    if (position instanceof PsiWhiteSpace || position.getNode().getElementType() == GroovyElementTypes.mNLS) {
      position = FilterPositionUtil.searchNonSpaceNonCommentBack(position);
    }

    boolean hasCommas = false;
    while (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof GrVariable) {
        PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(parent);
        hasCommas = prev != null && prev.getNode().getElementType() == GroovyElementTypes.mCOMMA;
      }

      if (parent instanceof GrClosableBlock) {
        PsiElement sibling = position.getPrevSibling();
        while (sibling != null) {
          if (sibling instanceof GrParameterList) {
            return hasCommas;
          }

          boolean isComma = sibling instanceof LeafPsiElement && GroovyElementTypes.mCOMMA == ((LeafPsiElement)sibling).getElementType();
          hasCommas |= isComma;

          if (isComma ||
              sibling instanceof PsiWhiteSpace ||
              sibling instanceof PsiErrorElement ||
              sibling instanceof GrVariableDeclaration ||
              sibling instanceof GrReferenceExpression && !((GrReferenceExpression)sibling).isQualified()
            ) {
            sibling = sibling.getPrevSibling();
          }
          else {
            return false;
          }
        }
        return false;
      }
      position = parent;
    }
    return false;
  }

  private static boolean semicolonNeeded(CompletionInitializationContext context) { //<caret>String name=
    HighlighterIterator iterator = ((EditorEx)context.getEditor()).getHighlighter().createIterator(context.getStartOffset());
    if (iterator.atEnd()) return false;

    if (iterator.getTokenType() == GroovyTokenTypes.mIDENT) {
      iterator.advance();
    }

    if (!iterator.atEnd() && iterator.getTokenType() == GroovyTokenTypes.mLPAREN) {
      return true;
    }

    while (!iterator.atEnd() && GroovyTokenTypes.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd() || iterator.getTokenType() != GroovyTokenTypes.mIDENT) return false;
    iterator.advance();

    while (!iterator.atEnd() && GroovyTokenTypes.WHITE_SPACES_OR_COMMENTS.contains(iterator.getTokenType())) {
      iterator.advance();
    }
    return true;
  }
}

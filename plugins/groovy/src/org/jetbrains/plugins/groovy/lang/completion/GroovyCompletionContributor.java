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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.*;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.completion.handlers.NamedArgumentInsertHandler;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.CompletionProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.util.containers.CollectionFactory.hashMap;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipWhitespaces;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {

  private static final ElementPattern<PsiElement> AFTER_DOT = psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);

  private static final String[] MODIFIERS =
    new String[]{GrModifier.PRIVATE, GrModifier.PUBLIC, GrModifier.PROTECTED, GrModifier.TRANSIENT, GrModifier.ABSTRACT, GrModifier.NATIVE,
      GrModifier.VOLATILE, GrModifier.STRICTFP, GrModifier.DEF, GrModifier.FINAL, GrModifier.SYNCHRONIZED, GrModifier.STATIC};
  private static final ElementPattern<PsiElement> TYPE_IN_VARIABLE_DECLARATION_AFTER_MODIFIER = PlatformPatterns
    .or(psiElement(PsiElement.class).withParent(GrVariable.class).afterLeaf(MODIFIERS),
        psiElement(PsiElement.class).withParent(GrParameter.class));

  private static final ElementPattern<PsiElement> IN_ARGUMENT_LIST_OF_CALL =
    psiElement().withParent(psiElement(GrReferenceExpression.class).withParent(psiElement(GrArgumentList.class).withParent(GrCall.class)));
  private static final ElementPattern<PsiElement> IN_MAP_KEY_ARGUMENT_LIST_OF_CALL =
    psiElement(GroovyTokenTypes.mIDENT).withParent(psiElement(GrArgumentLabel.class).withParent(psiElement(GrNamedArgument.class).withParent(psiElement(GrArgumentList.class).withParent(GrCall.class))));

  private static final String[] THIS_SUPER = {"this", "super"};
  private static final InsertHandler<JavaGlobalMemberLookupElement> STATIC_IMPORT_INSERT_HANDLER = new InsertHandler<JavaGlobalMemberLookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, JavaGlobalMemberLookupElement item) {
      GroovyInsertHandler.INSTANCE.handleInsert(context, item);
      final PsiClass containingClass = item.getContainingClass();
      PsiDocumentManager.getInstance(containingClass.getProject()).commitDocument(context.getDocument());
      final GrReferenceExpression ref = PsiTreeUtil
        .findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GrReferenceExpression.class, false);
      if (ref != null && ref.getQualifier() == null) {
        ref.bindToElementViaStaticImport(containingClass);
      }

    }
  };
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

  private static final CompletionProvider<CompletionParameters> MAP_ARGUMENT_COMPLETION_PROVIDER =
    new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final GrArgumentList argumentList;

        PsiElement parent = parameters.getPosition().getParent();
        if (parent instanceof GrReferenceExpression) {
          if (((GrReferenceExpression)parent).getQualifier() != null) return;
          argumentList = (GrArgumentList)parent.getParent();
        }
        else {
          argumentList = (GrArgumentList)parent.getParent().getParent();
        }

        final GrCall call = (GrCall)argumentList.getParent();
        List<GroovyResolveResult> results = new ArrayList<GroovyResolveResult>();
        //constructor call
        if (call instanceof GrConstructorCall) {
          GrConstructorCall constructorCall = (GrConstructorCall)call;
          ContainerUtil.addAll(results, constructorCall.multiResolveConstructor());
          ContainerUtil.addAll(results, constructorCall.multiResolveClass());
        }
        else if (call instanceof GrCallExpression) {
          GrCallExpression constructorCall = (GrCallExpression)call;
          ContainerUtil.addAll(results, constructorCall.getCallVariants(null));
          final PsiType type = ((GrCallExpression)call).getType();
          if (type instanceof PsiClassType) {
            final PsiClass psiClass = ((PsiClassType)type).resolve();
            results.add(new GroovyResolveResultImpl(psiClass, true));
          }
        }

        Set<PsiClass> usedClasses = new HashSet<PsiClass>();
        Set<String> usedNames = new HashSet<String>();
        for (GrNamedArgument argument : argumentList.getNamedArguments()) {
          final GrArgumentLabel label = argument.getLabel();
          if (label != null) {
            final String name = label.getName();
            if (name != null) {
              usedNames.add(name);
            }
          }
        }

        for (GroovyResolveResult resolveResult : results) {
          PsiElement element = resolveResult.getElement();
          if (element instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod)element;
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              addPropertiesForClass(result, usedClasses, usedNames, containingClass, call);
            }
            if (method instanceof GrMethod) {
              for (String parameter : ((GrMethod)method).getNamedParametersArray()) {
                if (!usedNames.contains(parameter)) {
                  final LookupElementBuilder lookup =
                    LookupElementBuilder.create(parameter).setIcon(GroovyIcons.DYNAMIC).setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
                  result.addElement(lookup);
                }
              }
            }
          }
          else if (element instanceof PsiClass) {
            addPropertiesForClass(result, usedClasses, usedNames, (PsiClass)element, call);
          }
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
    PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withElementType(
      elementType().oneOf(GroovyElementTypes.mNUM_DOUBLE, GroovyElementTypes.mNUM_INT, GroovyElementTypes.mNUM_LONG, GroovyElementTypes.mNUM_FLOAT, GroovyElementTypes.mNUM_BIG_INT, GroovyElementTypes.mNUM_BIG_DECIMAL)));


  private static void addAllClasses(CompletionParameters parameters, final CompletionResultSet result, final InheritorsHolder inheritors) {
    result.stopHere();
    AllClassesGetter.processJavaClasses(parameters, result.getPrefixMatcher(), parameters.getInvocationCount() <= 1, new Consumer<PsiClass>() {
      @Override
      public void consume(PsiClass psiClass) {
        if (!inheritors.alreadyProcessed(psiClass)) {
          result.addElement(GroovyCompletionUtil.createClassLookupItem(psiClass));
        }
      }
    });
  }

  public GroovyCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(PsiElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement reference = parameters.getPosition().getParent();
        if (reference instanceof GrReferenceElement) {
          completeReference(parameters, result, (GrReferenceElement)reference);
        }
      }
    });

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
        if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

        for (String keyword : THIS_SUPER) {
          result.addElement(LookupElementBuilder.create(keyword));
        }
      }
    });

    extend(CompletionType.BASIC, TYPE_IN_VARIABLE_DECLARATION_AFTER_MODIFIER, new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (!GroovyCompletionUtil.isFirstElementAfterModifiersInVariableDeclaration(position, true)) return;

        ResolverProcessor processor = CompletionProcessor.createClassCompletionProcessor(position);
        ResolveUtil.treeWalkUp((GrVariable)position.getParent(), processor, false);
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

          builder.setInsertHandler(GroovyInsertHandler.INSTANCE);
          result.addElement(builder);
        }
      }
    });

    extend(CompletionType.BASIC, IN_ARGUMENT_LIST_OF_CALL, MAP_ARGUMENT_COMPLETION_PROVIDER);
    extend(CompletionType.BASIC, IN_MAP_KEY_ARGUMENT_LIST_OF_CALL, MAP_ARGUMENT_COMPLETION_PROVIDER);

    // class name stuff

    extend(CompletionType.CLASS_NAME, psiElement().withParent(GrReferenceElement.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiElement position = parameters.getPosition();
        if (((GrReferenceElement)position.getParent()).getQualifier() != null) return;

        final String s = result.getPrefixMatcher().getPrefix();
        if (StringUtil.isEmpty(s) || !Character.isLowerCase(s.charAt(0))) return;

        completeStaticMembers(position).processStaticMethodsGlobally(result);
      }
    });


    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        addAllClasses(parameters, result, new InheritorsHolder(parameters.getPosition(), result));
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
    PsiElement position = parameters.getPosition();

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
    final PsiType qualifierType;
    if (qualifier instanceof GrExpression) {
      qualifierType = ((GrExpression)qualifier).getType();
    }
    else {
      qualifierType = null;
    }
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
        if (object instanceof GroovyResolveResult) {
          substitutor = ((GroovyResolveResult)object).getSubstitutor();
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

        if ((object instanceof PsiMethod || object instanceof PsiField) &&
            ((PsiModifierListOwner)object).hasModifierProperty(PsiModifier.STATIC)) {
          if (lookupElement.getLookupString().equals(((PsiMember)object).getName())) {
            staticMembers.put((PsiModifierListOwner)object, lookupElement);
            return;
          }
        }
        result.addElement(lookupElement);
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
          staticMembers.put(member, new JavaGlobalMemberLookupElement(member, psiClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER, true));

        }
      });

      final String s = result.getPrefixMatcher().getPrefix();
      if (!StringUtil.isEmpty(s) && Character.isUpperCase(s.charAt(0))) {
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
    CompletionService.getCompletionService()
      .setAdvertisementText(GroovyBundle.message("invoke.completion.second.time.to.show.skipped.methods"));
  }

  private static StaticMemberProcessor completeStaticMembers(PsiElement position) {
    final StaticMemberProcessor processor = new StaticMemberProcessor(position) {
      @NotNull
      @Override
      protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull PsiClass containingClass, boolean shouldImport) {
        return new JavaGlobalMemberLookupElement(member, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                                 shouldImport);
      }

      @Override
      protected LookupElement createLookupElement(@NotNull List<PsiMethod> overloads,
                                                  @NotNull PsiClass containingClass,
                                                  boolean shouldImport) {
        return new JavaGlobalMemberLookupElement(overloads, containingClass, QUALIFIED_METHOD_INSERT_HANDLER, STATIC_IMPORT_INSERT_HANDLER,
                                                 shouldImport);
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

  private static void addPropertiesForClass(CompletionResultSet result,
                                            Set<PsiClass> usedClasses,
                                            Set<String> usedNames,
                                            PsiClass containingClass,
                                            GrCall call) {
    if (usedClasses.contains(containingClass)) return;
    usedClasses.add(containingClass);
    final PsiClass eventListener =
      JavaPsiFacade.getInstance(call.getProject()).findClass("java.util.EventListener", call.getResolveScope());
    Map<String, PsiMethod> writableProperties = new HashMap<String, PsiMethod>();
    for (PsiMethod method : containingClass.getAllMethods()) {
      if (GroovyPropertyUtils.isSimplePropertySetter(method)) {
        if (PsiUtil.isStaticsOK(method, call)) {
          final String name = GroovyPropertyUtils.getPropertyNameBySetter(method);
          if (name != null && !writableProperties.containsKey(name)) {
            writableProperties.put(name, method);
          }
        }
      }
      else if (eventListener != null) {
        consumeListenerProperties(result, usedNames, method, eventListener);
      }
    }

    for (String name : writableProperties.keySet()) {
      if (usedNames.contains(name)) continue;
      usedNames.add(name);
      final LookupElementBuilder builder =
        LookupElementBuilder.create(writableProperties.get(name), name).setIcon(GroovyIcons.PROPERTY)
          .setInsertHandler(NamedArgumentInsertHandler.INSTANCE);
      result.addElement(builder);
    }
  }

  private static void consumeListenerProperties(CompletionResultSet result,
                                                Set<String> usedNames, PsiMethod method, PsiClass eventListenerClass) {
    if (method.getName().startsWith("add") && method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass listenerClass = classType.resolve();
        if (listenerClass != null) {
          final PsiMethod[] listenerMethods = listenerClass.getMethods();
          if (InheritanceUtil.isInheritorOrSelf(listenerClass, eventListenerClass, true)) {
            for (PsiMethod listenerMethod : listenerMethods) {
              final String name = listenerMethod.getName();
              usedNames.add(name);
              result.addElement(LookupElementBuilder.create(name).setIcon(GroovyIcons.PROPERTY).setInsertHandler(NamedArgumentInsertHandler.INSTANCE));
            }
          }
        }
      }
    }
  }

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    if (context.getCompletionType() == CompletionType.BASIC && context.getFile() instanceof GroovyFile) {
      if (semicolonNeeded(context)) {
        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + ";");
      }
      else if (isInClosurePropertyParameters(context.getFile().findElementAt(context.getStartOffset()))) {
        context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + "->");
      }
    }
  }

  public static boolean isInClosurePropertyParameters(PsiElement position) { //Closure cl={String x, <caret>...
    if (position == null) return false;

    GrVariableDeclaration declaration = PsiTreeUtil.getParentOfType(position, GrVariableDeclaration.class, false, GrStatement.class);
    if (declaration == null) {
      if (position.getParent() instanceof PsiErrorElement) position = position.getParent();
      PsiElement prev = position.getPrevSibling();
      prev = skipWhitespaces(prev, false);
      if (prev instanceof PsiErrorElement) {
        prev = prev.getPrevSibling();
      }
      prev = skipWhitespaces(prev, false);
      if (prev instanceof GrVariableDeclaration) declaration = (GrVariableDeclaration)prev;
    }
    if (declaration != null) {
      if (!(declaration.getParent() instanceof GrClosableBlock)) return false;
      PsiElement prevSibling = skipWhitespaces(declaration.getPrevSibling(), false);
      return prevSibling instanceof GrParameterList;
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

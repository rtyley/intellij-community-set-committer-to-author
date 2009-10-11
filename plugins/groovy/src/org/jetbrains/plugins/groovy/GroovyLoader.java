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

package org.jetbrains.plugins.groovy;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.refactoring.rename.RenameInputValidatorRegistry;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportsPassFactory;
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager;
import org.jetbrains.plugins.groovy.extensions.completion.InsertHandlerRegistry;
import org.jetbrains.plugins.groovy.lang.GroovyChangeUtilSupport;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionData;
import org.jetbrains.plugins.groovy.lang.editor.actions.GroovyEditorActionsManager;
import org.jetbrains.plugins.groovy.lang.groovydoc.completion.GroovyDocCompletionData;
import org.jetbrains.plugins.groovy.lang.groovydoc.completion.handlers.GroovyDocMethodHandler;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * Main application component, that loads Groovy language support
 *
 * @author ilyas
 */
public class GroovyLoader implements ApplicationComponent {

  public void initComponent() {
    GroovyEditorActionsManager.registerGroovyEditorActions();

    ChangeUtil.registerCopyHandler(new GroovyChangeUtilSupport());

    //Register Keyword completion
    setupCompletion();

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      public void projectOpened(final Project project) {
        final TextEditorHighlightingPassRegistrar registrar = TextEditorHighlightingPassRegistrar.getInstance(project);
        GroovyUnusedImportsPassFactory unusedImportsPassFactory = project.getComponent(GroovyUnusedImportsPassFactory.class);
        registrar.registerTextEditorHighlightingPass(unusedImportsPassFactory, new int[]{Pass.UPDATE_ALL}, null, true, -1);

        DebuggerManager.getInstance(project).registerPositionManagerFactory(new Function<DebugProcess, PositionManager>() {
          public PositionManager fun(DebugProcess debugProcess) {
            return new GroovyPositionManager(debugProcess);
          }
        });

      }
    });


    registerNameValidators();
  }

  private static void registerNameValidators() {
    RenameInputValidator validator = new RenameInputValidator() {
      public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
        return !GroovyRefactoringUtil.KEYWORDS.contains(newName);
      }
    };
    RenameInputValidatorRegistry.getInstance().registerInputValidator(psiElement(GrNamedElement.class), validator);
  }

  private static void setupCompletion() {
    InsertHandlerRegistry handlerRegistry = InsertHandlerRegistry.getInstance();
    handlerRegistry.registerSpecificInsertHandler(new GroovyDocMethodHandler());

    CompositeCompletionData compositeCompletionData = new CompositeCompletionData(new GroovyCompletionData(), new GroovyDocCompletionData());
    CompletionUtil.registerCompletionData(GroovyFileType.GROOVY_FILE_TYPE, compositeCompletionData);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "groovy.support.loader";
  }

}

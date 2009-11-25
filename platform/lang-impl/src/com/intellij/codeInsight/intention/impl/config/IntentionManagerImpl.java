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

package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.actions.RunInspectionIntention;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsInSuppressedPlaceIntention;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  @author dsl
 */
public class IntentionManagerImpl extends IntentionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl");

  private final List<IntentionAction> myActions = Collections.synchronizedList(new ArrayList<IntentionAction>());
  private final IntentionManagerSettings mySettings;

  private final Alarm myInitActionsAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  public IntentionManagerImpl(IntentionManagerSettings intentionManagerSettings) {
    mySettings = intentionManagerSettings;

    addAction(new EditInspectionToolsSettingsInSuppressedPlaceIntention());

    final ExtensionPoint<IntentionActionBean> point = Extensions.getArea(null).getExtensionPoint(EP_INTENTION_ACTIONS);

    point.addExtensionPointListener(new ExtensionPointListener<IntentionActionBean>() {
      public void extensionAdded(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
        registerIntentionFromBean(extension);
      }

      public void extensionRemoved(final IntentionActionBean extension, @Nullable final PluginDescriptor pluginDescriptor) {
      }
    });
  }

  private void registerIntentionFromBean(final IntentionActionBean extension) {
    final Runnable runnable = new Runnable() {
      public void run() {
        final String descriptionDirectoryName = extension.getDescriptionDirectoryName();
        final String[] categories = extension.getCategories();
        final IntentionAction instance = createIntentionActionWrapper(extension, categories);
        if (categories == null) {
          addAction(instance);
        }
        else {
          if (descriptionDirectoryName != null) {
            addAction(instance);
            mySettings.registerIntentionMetaData(instance, categories, descriptionDirectoryName, extension.getMetadataClassLoader());
          }
          else {
            registerIntentionAndMetaData(instance, categories);
          }
        }
      }
    };
    //todo temporary hack, need smarter logic:
    // * on the first request, wait until all the initialization is finished  
    // * ensure this request doesn't come on EDT
    // * while waiting, check for ProcessCanceledException
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    } else {
      myInitActionsAlarm.addRequest(runnable, 300);
    }
  }

  private static IntentionAction createIntentionActionWrapper(final IntentionActionBean intentionActionBean, final String[] categories) {
    return new IntentionActionWrapper(intentionActionBean,categories);
  }

  public void registerIntentionAndMetaData(IntentionAction action, String... category) {
    registerIntentionAndMetaData(action, category, getDescriptionDirectoryName(action));
  }

  @NotNull
  private static String getDescriptionDirectoryName(final IntentionAction action) {
    if (action instanceof IntentionActionWrapper) {
      final IntentionActionWrapper wrapper = (IntentionActionWrapper)action;
      return getDescriptionDirectoryName(wrapper.getImplementationClassName());
    }
    else {
      return getDescriptionDirectoryName(action.getClass().getName());
    }
  }

  private static String getDescriptionDirectoryName(final String fqn) {
    return fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll("\\$", "");
  }

  public void registerIntentionAndMetaData(@NotNull IntentionAction action, @NotNull String[] category, @NotNull @NonNls String descriptionDirectoryName) {
    addAction(action);
    mySettings.registerIntentionMetaData(action, category, descriptionDirectoryName);
  }

  public void registerIntentionAndMetaData(final IntentionAction action,
                                           final String[] category,
                                           final String description,
                                           final String exampleFileExtension,
                                           final String[] exampleTextBefore,
                                           final String[] exampleTextAfter) {
    addAction(action);

    IntentionActionMetaData metaData = new IntentionActionMetaData(action, category,
                                                                   new PlainTextDescriptor(description, "description.html"),
                                                                   mapToDescriptors(exampleTextBefore, "before." + exampleFileExtension),
                                                                   mapToDescriptors(exampleTextAfter, "after." + exampleFileExtension));
    mySettings.registerMetaData(metaData);
  }

  @Override
  public void unregisterIntention(IntentionAction intentionAction) {
    myActions.remove(intentionAction);
    mySettings.unregisterMetaData(intentionAction);
  }

  private static TextDescriptor[] mapToDescriptors(String[] texts, String fileName) {
    TextDescriptor[] result = new TextDescriptor[texts.length];
    for (int i = 0; i < texts.length; i++) {
      result [i] = new PlainTextDescriptor(texts [i], fileName);
    }
    return result;
  }

  public List<IntentionAction> getStandardIntentionOptions(@NotNull final HighlightDisplayKey displayKey, @NotNull final PsiElement context) {
    List<IntentionAction> options = new ArrayList<IntentionAction>(9);
    options.add(new EditInspectionToolsSettingsAction(displayKey));
    options.add(new RunInspectionIntention(displayKey));
    options.add(new DisableInspectionToolAction(displayKey));
    return options;
  }

  public LocalQuickFix convertToFix(final IntentionAction action) {
    if (action instanceof LocalQuickFix) {
      return (LocalQuickFix)action;
    }
    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return action.getText();
      }

      @NotNull
      public String getFamilyName() {
        return action.getFamilyName();
      }

      public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
        final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
        try {
          action.invoke(project, new LazyEditor(psiFile), psiFile);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
  }

  public void addAction(IntentionAction action) {
    myActions.add(action);
  }

  public IntentionAction[] getIntentionActions() {
    return myActions.toArray(new IntentionAction[myActions.size()]);
  }

}

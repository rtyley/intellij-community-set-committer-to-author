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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:53 PM
 */
public class ArrangementNodeComponentFactory {

  @NotNull private final ArrangementNodeDisplayManager myDisplayManager;
  @NotNull private final Runnable                      myRemoveConditionCallback;

  public ArrangementNodeComponentFactory(@NotNull ArrangementNodeDisplayManager manager,
                                         @NotNull Runnable removeConditionCallback)
  {
    myDisplayManager = manager;
    myRemoveConditionCallback = removeConditionCallback;
  }

  @NotNull
  public ArrangementNodeComponent getComponent(@NotNull final ArrangementMatchCondition node,
                                               @Nullable final ArrangementRuleEditingModel model)
  {
    final Ref<ArrangementNodeComponent> ref = new Ref<ArrangementNodeComponent>();
    node.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        ref.set(new ArrangementAtomNodeComponent(myDisplayManager, condition, prepareRemoveCallback(condition, model)));
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        switch (condition.getOperator()) {
          case AND:
            ref.set(new ArrangementAndNodeComponent(condition, ArrangementNodeComponentFactory.this, myDisplayManager, model)); break;
          case OR: // TODO den implement
        }
      }
    });
    return ref.get();
  }

  @Nullable
  private Runnable prepareRemoveCallback(@NotNull final ArrangementMatchCondition condition,
                                         @Nullable final ArrangementRuleEditingModel model)
  {
    if (model == null) {
      return null;
    }
    return new Runnable() {
      @Override
      public void run() {
        model.removeAndCondition(condition);
        myRemoveConditionCallback.run();
      }
    };
  }
}

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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    final Object o = element.getObject();

    if (o instanceof PsiLocalVariable || o instanceof PsiParameter || o instanceof PsiThisExpression) {
      return StatisticsInfo.EMPTY;
    }

    final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (infos != null && infos.length > 0) {
      final boolean primitivesOnly = ContainerUtil.and(infos, new Condition<ExpectedTypeInfo>() {
        public boolean value(final ExpectedTypeInfo info) {
          return info.getType() instanceof PsiPrimitiveType;
        }
      });
      if (primitivesOnly) {
        return StatisticsInfo.EMPTY; //collecting statistics on primitive types usually has only negative effects
      }
    }

    LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
    if (item == null) return null;

    PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);

    if (o instanceof PsiMember) {
      String key2 = JavaStatisticsManager.getMemberUseKey2((PsiMember)o);
      if (o instanceof PsiClass) {
        PsiType expectedType = infos != null && infos.length > 0 ? infos[0].getDefaultType() : null;
        return new StatisticsInfo(JavaStatisticsManager.getAfterNewKey(expectedType), key2);
      }
      return new StatisticsInfo(JavaStatisticsManager.getMemberUseKey2(((PsiMember)o).getContainingClass()), key2);
    }

    if (qualifierType != null) return StatisticsInfo.EMPTY;

    return null;
  }

}

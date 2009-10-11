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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:18:10
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.refactoring.classMembers.DependencyMemberInfoModel;
import com.intellij.refactoring.classMembers.MemberInfoTooltipManager;

public class InterfaceDependencyMemberInfoModel extends DependencyMemberInfoModel<PsiMember, MemberInfo> {

  public InterfaceDependencyMemberInfoModel(PsiClass aClass) {
    super(new InterfaceMemberDependencyGraph(aClass), WARNING);
    setTooltipProvider(new MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>() {
      public String getTooltip(MemberInfo memberInfo) {
        return ((InterfaceMemberDependencyGraph) myMemberDependencyGraph).getElementTooltip(memberInfo.getMember());
      }
    });
  }

  public boolean isCheckedWhenDisabled(MemberInfo member) {
    return false;
  }

  public Boolean isFixedAbstract(MemberInfo member) {
    return null;
  }
}

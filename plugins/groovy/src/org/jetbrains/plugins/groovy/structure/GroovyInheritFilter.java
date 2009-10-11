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

package org.jetbrains.plugins.groovy.structure;
import com.intellij.ide.structureView.impl.java.InheritedMembersFilter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import org.jetbrains.plugins.groovy.structure.elements.impl.GroovyMethodStructureViewElement;

/**
 * Created by IntelliJ IDEA.
 * User: Dmitry.Krasilschikov
 * Date: 06.01.2009
 * Time: 15:02:27
 * To change this template use File | Settings | File Templates.
 */
public class GroovyInheritFilter extends InheritedMembersFilter {
  @Override
  public boolean isVisible(TreeElement treeNode) {
    if (treeNode instanceof GroovyMethodStructureViewElement){
      return !((GroovyMethodStructureViewElement)treeNode).isInherit();
    }
    return super.isVisible(treeNode);
  }
}

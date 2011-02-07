/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.treeWithCheckedNodes;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.TreeNodeState;
import com.intellij.util.containers.Convertor;

import javax.swing.tree.DefaultMutableTreeNode;

/**
* @author irengrig
*         Date: 2/7/11
*         Time: 10:43 AM
 *
 * see {@link SelectedState}
*/
public class SelectionManager {
  private final SelectedState<VirtualFile> myState;
  private final Convertor<DefaultMutableTreeNode, VirtualFile> myNodeConvertor;

  public SelectionManager(int selectedSize, int queueSize, final Convertor<DefaultMutableTreeNode, VirtualFile> nodeConvertor) {
    myNodeConvertor = nodeConvertor;
    myState = new SelectedState<VirtualFile>(selectedSize, queueSize);
  }

  public void toggleSelection(final DefaultMutableTreeNode node) {
    final StateWorker stateWorker = new StateWorker(node, myNodeConvertor);
    if (stateWorker.getVf() == null) return;

    final TreeNodeState state = getStateImpl(stateWorker);
    if (TreeNodeState.HAVE_SELECTED_ABOVE.equals(state)) return;
    if (TreeNodeState.CLEAR.equals(state) && (! myState.canAddSelection())) return;

    final TreeNodeState futureState =
      myState.putAndPass(stateWorker.getVf(), TreeNodeState.SELECTED.equals(state) ? TreeNodeState.CLEAR : TreeNodeState.SELECTED);

    // for those possibly duplicate nodes (i.e. when we have root for module and root for VCS root, each file is shown twice in a tree ->
    // clear all suspicious cached)
    if (! TreeNodeState.SELECTED.equals(futureState)) {
      myState.clearAllCachedMatching(new Processor<VirtualFile>() {
        @Override
        public boolean process(VirtualFile virtualFile) {
          return VfsUtil.isAncestor(virtualFile, stateWorker.getVf(), false);
        }
      });
    }
    stateWorker.iterateParents(myState, new PairProcessor<VirtualFile, TreeNodeState>() {
      @Override
      public boolean process(VirtualFile virtualFile, TreeNodeState state) {
        if (TreeNodeState.SELECTED.equals(futureState)) {
          myState.putAndPass(virtualFile, TreeNodeState.HAVE_SELECTED_BELOW);
        } else {
          myState.remove(virtualFile);
        }
        return true;
      }
    });
    myState.clearAllCachedMatching(new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile vf) {
        return VfsUtil.isAncestor(stateWorker.getVf(), vf, false);
      }
    });
    for (VirtualFile selected : myState.getSelected()) {
      if (VfsUtil.isAncestor(stateWorker.getVf(), selected, true)) {
        myState.remove(selected);
      }
    }
  }

  public TreeNodeState getState(final DefaultMutableTreeNode node) {
    return getStateImpl(new StateWorker(node, myNodeConvertor));
  }

  private TreeNodeState getStateImpl(final StateWorker stateWorker) {
    if (stateWorker.getVf() == null) return TreeNodeState.CLEAR;

    final TreeNodeState stateSelf = myState.get(stateWorker.getVf());
    if (stateSelf != null) return stateSelf;

    final Ref<TreeNodeState> result = new Ref<TreeNodeState>();
    stateWorker.iterateParents(myState, new PairProcessor<VirtualFile, TreeNodeState>() {
      @Override
      public boolean process(VirtualFile virtualFile, TreeNodeState state) {
        if (state != null) {
          if (TreeNodeState.SELECTED.equals(state) || TreeNodeState.HAVE_SELECTED_ABOVE.equals(state)) {
            result.set(myState.putAndPass(stateWorker.getVf(), TreeNodeState.HAVE_SELECTED_ABOVE));
          }
          return false; // exit
        }
        return true;
      }
    });

    if (! result.isNull()) return  result.get();

    for (VirtualFile selected : myState.getSelected()) {
      if (VfsUtil.isAncestor(stateWorker.getVf(), selected, true)) {
        return myState.putAndPass(stateWorker.getVf(), TreeNodeState.HAVE_SELECTED_BELOW);
      }
    }
    return TreeNodeState.CLEAR;
  }

  private static class StateWorker {
    private final DefaultMutableTreeNode myNode;
    private final Convertor<DefaultMutableTreeNode, VirtualFile> myConvertor;
    private VirtualFile myVf;

    private StateWorker(DefaultMutableTreeNode node, final Convertor<DefaultMutableTreeNode, VirtualFile> convertor) {
      myNode = node;
      myConvertor = convertor;
      myVf = myConvertor.convert(node);
    }

    public VirtualFile getVf() {
      return myVf;
    }

    public void iterateParents(final SelectedState<VirtualFile> states, final PairProcessor<VirtualFile, TreeNodeState> parentsProcessor) {
      DefaultMutableTreeNode current = (DefaultMutableTreeNode) myNode.getParent();
      // up cycle
      while (current != null) {
        final VirtualFile file = myConvertor.convert(current);
        if (file == null) return;

        final TreeNodeState state = states.get(file);
        if (! parentsProcessor.process(file, state)) return;
        current = (DefaultMutableTreeNode)current.getParent();
      }
    }
  }
}

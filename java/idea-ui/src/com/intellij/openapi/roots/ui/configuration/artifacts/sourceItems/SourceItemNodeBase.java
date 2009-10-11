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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ArtifactsTreeNode;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsProvider;
import com.intellij.packaging.ui.TreeNodePresentation;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class SourceItemNodeBase extends ArtifactsTreeNode {
  private Artifact myArtifact;
  private final ArtifactEditorEx myArtifactEditor;

  public SourceItemNodeBase(ArtifactEditorContext context, NodeDescriptor parentDescriptor, final TreeNodePresentation presentation,
                            ArtifactEditorEx artifactEditor) {
    super(context, parentDescriptor, presentation);
    myArtifact = artifactEditor.getArtifact();
    myArtifactEditor = artifactEditor;
  }

  protected ArtifactEditorEx getArtifactEditor() {
    return myArtifactEditor;
  }

  @Override
  protected void update(PresentationData presentation) {
    final Artifact artifact = myArtifactEditor.getArtifact();
    if (!myArtifact.equals(artifact)) {
      myArtifact = artifact;
    }
    super.update(presentation);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    final PackagingSourceItemsProvider[] providers = Extensions.getExtensions(PackagingSourceItemsProvider.EP_NAME);
    List<SimpleNode> children = new ArrayList<SimpleNode>();
    for (PackagingSourceItemsProvider provider : providers) {
      final Collection<? extends PackagingSourceItem> items = provider.getSourceItems(myContext, myArtifact, getSourceItem());
      for (PackagingSourceItem item : items) {
        if (myArtifact.getArtifactType().isSuitableItem(item)) {
          children.add(new SourceItemNode(myContext, this, item, myArtifactEditor));
        }
      }
    }
    return children.toArray(new SimpleNode[children.size()]);
  }

  @Nullable
  protected abstract PackagingSourceItem getSourceItem();
}

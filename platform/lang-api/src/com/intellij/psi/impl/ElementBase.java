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

package com.intellij.psi.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.util.PsiIconUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.ElementBase");

  public static final int FLAGS_LOCKED = 0x800;

  @Nullable
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;

    try {
      final PsiElement element = (PsiElement)this;

      final Icon providersIcon = PsiIconUtil.getProvidersIcon(element, flags);
      if (providersIcon != null) {
        return providersIcon instanceof RowIcon ? (RowIcon)providersIcon : createLayeredIcon(providersIcon, flags);
      }

      return getElementIcon(flags);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (IndexNotReadyException e) {
      throw e;
    }
    catch(Exception e) {
      LOG.error(e);
        return null;
      }
    }

  protected Icon getElementIcon(final int flags) {
    final PsiElement element = (PsiElement)this;
    RowIcon baseIcon;
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;
    if (element instanceof ItemPresentation && ((ItemPresentation)element).getIcon(false) != null) {
        baseIcon = createLayeredIcon(((ItemPresentation)element).getIcon(false), elementFlags);
    }
    else if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;

      VirtualFile virtualFile = file.getVirtualFile();
      final Icon fileTypeIcon;
      if (virtualFile == null) {
        fileTypeIcon = file.getFileType().getIcon();
      }
      else {
        fileTypeIcon = IconUtil.getIcon(virtualFile, flags & ~ICON_FLAG_READ_STATUS, file.getProject());
      }
      return createLayeredIcon(fileTypeIcon, elementFlags);
    }
    else {
      return null;
    }
    return baseIcon;
  }

  public static RowIcon createLayeredIcon(Icon icon, int flags) {
    if (flags != 0) {
      List<Icon> iconLayers = new SmartList<Icon>();
      for(IconLayer l: ourIconLayers) {
        if ((flags & l.flagMask) != 0) {
          iconLayers.add(l.icon);
        }
      }
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i+1);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }

  public static int transformFlags(PsiElement element, int _flags) {
    int flags = 0;
    final boolean isLocked = (_flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    if (isLocked) flags |= FLAGS_LOCKED;
    return flags;
  }

  private static class IconLayer {
    int flagMask;
    Icon icon;

    IconLayer(final int flagMask, final Icon icon) {
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static final List<IconLayer> ourIconLayers = new ArrayList<IconLayer>();

  public static void registerIconLayer(int flagMask, Icon icon) {
    for(IconLayer iconLayer: ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
  }

  static {
    registerIconLayer(FLAGS_LOCKED, Icons.LOCKED_ICON);
  }
}
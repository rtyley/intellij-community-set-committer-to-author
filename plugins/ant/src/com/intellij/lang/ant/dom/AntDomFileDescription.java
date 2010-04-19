/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.ForcedAntFileAttribute;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 6, 2010
 */
public class AntDomFileDescription extends DomFileDescription<AntDomProject> {
  private static final String PROJECT_TAG_NAME = "project";
  private static final String DEFAULT_ATTRIB_NAME = "default";

  public AntDomFileDescription() {
    super(AntDomProject.class, PROJECT_TAG_NAME);
  }

  protected void initializeFileDescription() {
    registerReferenceInjector(new AntReferenceInjector());
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return super.isMyFile(file, module) && isAntFile(file);
  }

  public static boolean isAntFile(final XmlFile xmlFile) {
    final XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null && PROJECT_TAG_NAME.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
        if (tag.getAttributeValue(DEFAULT_ATTRIB_NAME) != null) {
          return true;
        }
        final VirtualFile vFile = xmlFile.getOriginalFile().getVirtualFile();
        if (vFile != null && ForcedAntFileAttribute.isAntFile(vFile)) {
          return true;
        }
      }
    }
    return false;
  }

}

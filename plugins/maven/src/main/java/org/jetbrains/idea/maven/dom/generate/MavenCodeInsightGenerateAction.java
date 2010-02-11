package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.TypeNameManager;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

/**
 * User: Sergey.Vasiliev
 */
public class MavenCodeInsightGenerateAction extends GenerateDomElementAction {

  public MavenCodeInsightGenerateAction(@NotNull Class<? extends DomElement> childElementClass,
                                        @Nullable final String mappingId,
                                        @NotNull Function<MavenDomProjectModel, DomElement> parentFunction) {
    this(TypeNameManager.getTypeName(childElementClass), childElementClass, mappingId, parentFunction);
  }

  public MavenCodeInsightGenerateAction(@NotNull final String description,
                                        @NotNull final Class<? extends DomElement> childElementClass,
                                        @Nullable final String mappingId,
                                        @NotNull Function<MavenDomProjectModel, DomElement> parentFunction) {
    super(new MavenGenerateDomElementProvider(description, childElementClass, mappingId, parentFunction));

    getTemplatePresentation().setIcon(ElementPresentationManager.getIconForClass(childElementClass));
  }

  public MavenCodeInsightGenerateAction(final GenerateDomElementProvider generateProvider) {
    super(generateProvider);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return file instanceof XmlFile && MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class) != null;
  }

}
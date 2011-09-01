package org.jetbrains.plugins.gradle.importing.wizard;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NotNull;

/**
 * Just a holder for the common useful functionality.
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 3:22 PM
 */
public abstract class AbstractImportFromGradleWizardStep extends ProjectImportWizardStep {

  protected AbstractImportFromGradleWizardStep(@NotNull WizardContext context) {
    super(context);
  }

  @Override
  public WizardContext getWizardContext() {
    return super.getWizardContext();
  }

  @Override
  @NotNull
  protected GradleProjectImportBuilder getBuilder() {
    return (GradleProjectImportBuilder)getWizardContext().getProjectBuilder();
  }
}

package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class GradleLibraryDependency extends AbstractGradleDependency<GradleLibrary> implements Named {

  public GradleLibraryDependency(@NotNull GradleLibrary library) {
    super(library);
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this); 
  }

  @NotNull
  @Override
  public GradleLibraryDependency clone(@NotNull GradleEntityCloneContext context) {
    GradleLibraryDependency result = new GradleLibraryDependency(getTarget().clone(context));
    copyTo(result);
    return result;
  }
}

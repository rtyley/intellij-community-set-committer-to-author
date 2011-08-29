package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.*;

/**
 * Module settings holder.
 * <p/>
 * <b>Note:</b> doesn't consider {@link GradleLibraryDependency library} {@link #getDependencies() dependencies}
 * at {@link #equals(Object)}/{@link #hashCode()}.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class GradleModule extends AbstractNamedGradleEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final List<GradleContentRoot> myContentRoots       = new ArrayList<GradleContentRoot>();
  private final Map<SourceType, String> myCompileOutputPaths = new HashMap<SourceType, String>();
  private final Set<GradleDependency>   myDependencies       = new HashSet<GradleDependency>();
  
  private String myModuleFilePath;
  private boolean myInheritProjectCompileOutputPath = true;
  
  public GradleModule(@NotNull String name, @NotNull String moduleFileDirectoryPath) {
    super(name);
    setModuleFileDirectoryPath(moduleFileDirectoryPath);
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void setModuleFileDirectoryPath(@NotNull String path) {
    myModuleFilePath = GradleUtil.toCanonicalPath(path + "/" + getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @NotNull
  public Collection<GradleContentRoot> getContentRoots() {
    return myContentRoots;
  }

  public void addContentRoot(@NotNull GradleContentRoot contentRoot) {
    myContentRoots.add(contentRoot);
  }

  public boolean isInheritProjectCompileOutputPath() {
    return myInheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    myInheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   *
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link GradleProject#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  public String getCompileOutputPath(@NotNull SourceType type) {
    return myCompileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull SourceType type, @Nullable String path) {
    if (path == null) {
      myCompileOutputPaths.remove(type);
      return;
    }
    myCompileOutputPaths.put(type, GradleUtil.toCanonicalPath(path));
  }

  @NotNull
  public Collection<GradleDependency> getDependencies() {
    return myDependencies;
  }

  public void addDependency(@NotNull GradleDependency dependency) {
    myDependencies.add(dependency);
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModuleFilePath.hashCode();
    result = 31 * result + (myInheritProjectCompileOutputPath ? 1 : 0);
    result = 31 * result + myCompileOutputPaths.hashCode();
    result = 31 * result + myContentRoots.hashCode();
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleModule that = (GradleModule)o;

    if (!super.equals(that)) return false;
    if (!myModuleFilePath.equals(that.myModuleFilePath)) return false;
    if (myInheritProjectCompileOutputPath != that.myInheritProjectCompileOutputPath) return false;
    if (!myCompileOutputPaths.equals(that.myCompileOutputPaths)) return false;
    if (!myContentRoots.equals(that.myContentRoots)) return false;
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return true;
  }

  @Override
  public String toString() {
    return String.format(
      "module '%s'. Content roots: %s; inherit compile output path: %b",
      getName(), getContentRoots(), isInheritProjectCompileOutputPath()
    );
  }

  @Override
  public GradleModule clone() {
    GradleModule result = new GradleModule(getName(), new File(getModuleFilePath()).getParent());
    for (GradleContentRoot contentRoot : getContentRoots()) {
      result.addContentRoot(contentRoot.clone());
    }
    for (Map.Entry<SourceType, String> entry : myCompileOutputPaths.entrySet()) {
      result.setCompileOutputPath(entry.getKey(), entry.getValue());
    }
    for (GradleDependency dependency : getDependencies()) {
      result.addDependency(dependency.clone());
    }
    return result;
  }
}

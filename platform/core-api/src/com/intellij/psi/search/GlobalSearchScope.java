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
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class GlobalSearchScope extends SearchScope implements ProjectAwareFileFilter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.GlobalSearchScope");
  @Nullable private final Project myProject;

  protected GlobalSearchScope(Project project) {
    myProject = project;
  }

  protected GlobalSearchScope() {
    this(null);
  }

  public abstract boolean contains(VirtualFile file);

  public Project getProject() {
    return myProject;
  }

  /**
   * @param file1
   * @param file2
   * @return a positive integer (+1), if file1 is located in the classpath before file2,
   *         a negative integer (-1), if file1 is located in the classpath after file2
   *         zero - otherwise or when the file are not comparable.
   */
  public abstract int compare(VirtualFile file1, VirtualFile file2);

  // optimization methods:

  public abstract boolean isSearchInModuleContent(@NotNull Module aModule);

  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule);
  }

  public boolean accept(VirtualFile file) {
    return contains(file);
  }

  public abstract boolean isSearchInLibraries();

  public boolean isSearchOutsideRootModel() {
    return false;
  }

  @NotNull
  public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return this;
    if (scope instanceof IntersectionScope) {
      return scope.intersectWith(this);
    }
    return new IntersectionScope(this, scope, null);
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    if (scope2 instanceof LocalSearchScope) {
      LocalSearchScope localScope2 = (LocalSearchScope)scope2;
      return intersectWith(localScope2);
    }
    return intersectWith((GlobalSearchScope)scope2);
  }

  public SearchScope intersectWith(LocalSearchScope localScope2) {
    PsiElement[] elements2 = localScope2.getScope();
    List<PsiElement> result = new ArrayList<PsiElement>(elements2.length);
    for (final PsiElement element2 : elements2) {
      if (PsiSearchScopeUtil.isInScope(this, element2)) {
        result.add(element2);
      }
    }
    return new LocalSearchScope(result.toArray(new PsiElement[result.size()]), null, localScope2.isIgnoreInjectedPsi());
  }

  @NotNull
  public GlobalSearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof GlobalSearchScope) return uniteWith((GlobalSearchScope)scope);
    return union((LocalSearchScope)scope);
  }

  @NotNull
  public GlobalSearchScope union(final LocalSearchScope scope) {
    return new GlobalSearchScope(scope.getScope()[0].getProject()) {
      @Override
      public boolean contains(VirtualFile file) {
        return GlobalSearchScope.this.contains(file) || scope.isInScope(file);
      }

      @Override
      public int compare(VirtualFile file1, VirtualFile file2) {
        return GlobalSearchScope.this.contains(file1) && GlobalSearchScope.this.contains(file2) ? GlobalSearchScope.this.compare(file1, file2) : 0;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return GlobalSearchScope.this.isSearchInModuleContent(aModule);
      }

      @Override
      public boolean isSearchOutsideRootModel() {
        return GlobalSearchScope.this.isSearchOutsideRootModel();
      }

      @Override
      public boolean isSearchInLibraries() {
        return GlobalSearchScope.this.isSearchInLibraries();
      }
    };
  }

  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return scope;
    return new UnionScope(this, scope, null);
  }

  public static GlobalSearchScope allScope(@NotNull Project project) {
    return ProjectScope.getAllScope(project);
  }

  public static GlobalSearchScope projectScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  public static GlobalSearchScope notScope(@NotNull final GlobalSearchScope scope) {
    return new DelegatingGlobalSearchScope(scope) {
      public boolean contains(final VirtualFile file) {
        return !myBaseScope.contains(file);
      }

      @Override
      public boolean isSearchOutsideRootModel() {
        return true;
      }
    };
  }

  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources and tests, excluding libraries and dependencies.
   */
  public static GlobalSearchScope moduleScope(@NotNull Module module) {
    return module.getModuleScope();
  }

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and libraries, excluding dependencies.
   */
  public static GlobalSearchScope moduleWithLibrariesScope(@NotNull Module module) {
    return module.getModuleWithLibrariesScope();
  }

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and dependencies, excluding libraries.
   */
  public static GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  public static GlobalSearchScope moduleRuntimeScope(@NotNull Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  public static GlobalSearchScope moduleWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  public static GlobalSearchScope moduleTestsWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  static class IntersectionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private final String myDisplayName;

    IntersectionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2, String displayName) {
      super(scope1.getProject() == null ? scope2.getProject() : scope1.getProject());
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
    }

    @NotNull
    @Override
    public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
      if (myScope1.equals(scope) || myScope2.equals(scope)) {
        return this;
      }
      return new IntersectionScope(this, scope, null);
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) && myScope2.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.compare(file1, file2);
      int res2 = myScope2.compare(file1, file2);

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) && myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return myScope1.isSearchInModuleContent(aModule, testSources) && myScope2.isSearchInModuleContent(aModule, testSources);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() && myScope2.isSearchInLibraries();
    }
    
    public boolean isSearchOutsideRootModel() {
      return myScope1.isSearchOutsideRootModel() && myScope2.isSearchOutsideRootModel();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IntersectionScope)) return false;

      IntersectionScope that = (IntersectionScope)o;

      if (!myScope1.equals(that.myScope1)) return false;
      if (!myScope2.equals(that.myScope2)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * myScope1.hashCode() + myScope2.hashCode();
    }
  }
  private static class UnionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private final String myDisplayName;

    private UnionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2, String displayName) {
      super(scope1.getProject() == null ? scope2.getProject() : scope1.getProject());
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
    }

    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.union", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    public boolean contains(VirtualFile file) {
      return myScope1.contains(file) || myScope2.contains(file);
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return myScope1.isSearchOutsideRootModel() || myScope2.isSearchOutsideRootModel();
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      int res1 = myScope1.contains(file1) && myScope1.contains(file2) ? myScope1.compare(file1, file2) : 0;
      int res2 = myScope2.contains(file1) && myScope2.contains(file2) ? myScope2.compare(file1, file2) : 0;

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) || myScope2.isSearchInModuleContent(aModule);
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return myScope1.isSearchInModuleContent(aModule, testSources) || myScope2.isSearchInModuleContent(aModule, testSources);
    }

    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() || myScope2.isSearchInLibraries();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UnionScope)) return false;

      UnionScope that = (UnionScope)o;

      if (!myScope1.equals(that.myScope1)) return false;
      if (!myScope2.equals(that.myScope2)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return 31 * myScope1.hashCode() + myScope2.hashCode();
    }
  }

  @NotNull
  public static GlobalSearchScope getScopeRestrictedByFileTypes (@NotNull GlobalSearchScope scope, final FileType... fileTypes) {
    LOG.assertTrue(fileTypes.length > 0);
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  private static class FileTypeRestrictionScope extends DelegatingGlobalSearchScope {
    private final FileType[] myFileTypes;

    private FileTypeRestrictionScope(@NotNull GlobalSearchScope scope, @NotNull FileType[] fileTypes) {
      super(scope);
      myFileTypes = fileTypes;
    }

    public boolean contains(VirtualFile file) {
      if (!super.contains(file)) return false;

      final FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFile(file);
      for (FileType otherFileType : myFileTypes) {
        if (fileType.equals(otherFileType)) return true;
      }

      return false;
    }

    @NotNull
    @Override
    public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
      if (scope instanceof FileTypeRestrictionScope) {
        FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
        if (restrict.myBaseScope == myBaseScope) {
          List<FileType> intersection = new ArrayList<FileType>(Arrays.asList(restrict.myFileTypes));
          intersection.retainAll(Arrays.asList(myFileTypes));
          return new FileTypeRestrictionScope(myBaseScope, intersection.toArray(new FileType[intersection.size()]));
        }
      }
      return super.intersectWith(scope);
    }

    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (scope instanceof FileTypeRestrictionScope) {
        FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
        if (restrict.myBaseScope == myBaseScope) {
          return new FileTypeRestrictionScope(myBaseScope, ArrayUtil.mergeArrays(myFileTypes, restrict.myFileTypes));
        }
      }
      return super.uniteWith(scope);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileTypeRestrictionScope)) return false;

      FileTypeRestrictionScope that = (FileTypeRestrictionScope)o;

      if (!Arrays.equals(myFileTypes, that.myFileTypes)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Arrays.hashCode(myFileTypes);
      return result;
    }
  }

  private static class EmptyScope extends GlobalSearchScope {
    public boolean contains(VirtualFile file) {
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return 0;
    }

    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    @NotNull
    public GlobalSearchScope intersectWith(@NotNull final GlobalSearchScope scope) {
      return this;
    }

    public GlobalSearchScope uniteWith(@NotNull final GlobalSearchScope scope) {
      return scope;
    }
  }

  public static final GlobalSearchScope EMPTY_SCOPE = new EmptyScope();
}

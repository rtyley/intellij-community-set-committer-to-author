package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class GppLibraryManager extends AbstractGroovyLibraryManager {
  private static final Pattern GROOVYPP_JAR = Pattern.compile("groovypp-([\\d\\.]+)\\.jar");
  private static final Pattern GROOVYPP_ALL_JAR = Pattern.compile("groovypp-all-([\\d\\.]+)\\.jar");

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File lib = new File(path + "/lib");
    if (lib.exists()) {
      libraryEditor.addJarDirectory(VfsUtil.getUrlForLibraryRoot(lib), false);
    }

    File srcRoot = new File(path + "/src");
    addSources(libraryEditor, srcRoot.exists() ? srcRoot : new File(path));
  }

  private static void addSources(LibraryEditor libraryEditor, File srcRoot) {
    File compilerSrc = new File(srcRoot, "Compiler/src");
    if (compilerSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(compilerSrc), OrderRootType.SOURCES);
    }

    File stdLibSrc = new File(srcRoot, "StdLib/src");
    if (stdLibSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(stdLibSrc), OrderRootType.SOURCES);
    }

    File mainSrc = new File(srcRoot, "main");
    if (mainSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(mainSrc), OrderRootType.SOURCES);
    }
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return getGppVersion(libraryFiles) != null;
  }

  @Nls
  @Override
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    return getGppVersion(libraryFiles);
  }

  @Nullable
  private static String getGppVersion(VirtualFile[] files) {
    for (VirtualFile file : files) {
      Matcher matcher = GROOVYPP_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }

      matcher = GROOVYPP_ALL_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    assert file != null;
    final VirtualFile libDir = file.findChild("lib");
    assert libDir != null;
    final String version = getGppVersion(libDir.getChildren());
    if (version != null) {
      return version;
    }
    throw new AssertionError(path);
  }


  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy++";
  }

  public boolean managesName(@NotNull String name) {
    return super.managesName(name) || StringUtil.startsWithIgnoreCase(name, "groovypp");
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    final VirtualFile libDir = file.findChild("lib");
    return libDir != null && getGppVersion(libDir.getChildren()) != null;
  }
}

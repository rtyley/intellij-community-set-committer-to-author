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

package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.CustomArtifact;
import static org.jetbrains.idea.maven.project.MavenId.append;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class MavenArtifact implements Serializable {
  private String myGroupId;
  private String myArtifactId;
  private String myVersion;
  private String myBaseVersion;
  private String myType;
  private String myClassifier;

  private String myScope;
  private boolean myOptional;

  private String myExtension;

  private File myFile;
  private boolean myResolved;
  private boolean myStubbed;

  private List<String> myTrail;

  protected MavenArtifact() {
  }

  public MavenArtifact(Artifact artifact, File localRepository) {
    myGroupId = artifact.getGroupId();
    myArtifactId = artifact.getArtifactId();
    myVersion = artifact.getVersion();
    myBaseVersion = artifact.getBaseVersion();
    myType = artifact.getType();
    myClassifier = artifact.getClassifier();

    myScope = artifact.getScope();
    myOptional = artifact.isOptional();

    myExtension = getExtension(artifact);

    myFile = artifact.getFile();
    if (myFile == null) {
      myFile = new File(localRepository, getRelativePath());
    }

    myResolved = artifact.isResolved();
    myStubbed = artifact instanceof CustomArtifact && ((CustomArtifact)artifact).isStub();

    List<String> originalTrail = artifact.getDependencyTrail();
    myTrail = originalTrail == null || originalTrail.isEmpty()
              ? new ArrayList<String>()
              : new ArrayList<String>(originalTrail.subList(1, originalTrail.size()));
  }

  private String getExtension(Artifact artifact) {
    ArtifactHandler handler = artifact.getArtifactHandler();
    String result = null;
    if (handler != null) result = handler.getExtension();
    if (result == null) result = artifact.getType();
    return result;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  public String getVersion() {
    return myVersion;
  }

  public MavenId getMavenId() {
    return new MavenId(myGroupId, myArtifactId, myVersion);
  }

  public String getType() {
    return myType;
  }

  public String getClassifier() {
    return myClassifier;
  }

  public String getScope() {
    return myScope;
  }

  public boolean isOptional() {
    return myOptional;
  }

  public boolean isExportable() {
    if (myOptional) return false;
    return Artifact.SCOPE_COMPILE.equals(myScope) || Artifact.SCOPE_RUNTIME.equals(myScope);
  }

  public String getExtension() {
    return myExtension;
  }

  public boolean isResolved() {
    return myResolved && myFile.exists() && !myStubbed;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  public String getPath() {
    return FileUtil.toSystemIndependentName(myFile.getPath());
  }

  public String getRelativePath() {
    return getRelativePathForClassifier(null);
  }

  public String getRelativePathForClassifier(String extraClassifier) {
    StringBuilder result = new StringBuilder();
    result.append(myGroupId.replace('.', '/'));
    result.append('/');
    result.append(myArtifactId);
    result.append('/');
    result.append(myVersion);
    result.append('/');
    result.append(myArtifactId);
    result.append('-');
    result.append(myVersion);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) {
      result.append('-');
      result.append(myClassifier);
    }

    if (!StringUtil.isEmptyOrSpaces(extraClassifier)) {
      result.append('-');
      result.append(extraClassifier);
      result.append(".jar");
    }
    else {
      result.append(".");
      result.append(myExtension);
    }
    return result.toString();
  }

  public String getUrl() {
    return getUrlForClassifier(null);
  }

  public String getUrlForClassifier(String extraClassifier) {
    String path = getPath();

    if (!StringUtil.isEmptyOrSpaces(extraClassifier)) {
      int dotPos = path.lastIndexOf(".");
      if (dotPos != -1) {// sometimes path doesn't contain '.'; but i can't find any reason.
        String withoutExtension = path.substring(0, dotPos);
        path = MessageFormat.format("{0}-{1}.jar", withoutExtension, extraClassifier);
      }
    }
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, path) + JarFileSystem.JAR_SEPARATOR;
  }

  public List<String> getTrail() {
    return myTrail;
  }

  public String getDisplayStringSimple() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringWithType() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myType);
    append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringWithTypeAndClassifier() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) append(builder, myClassifier);
    append(builder, myVersion);

    return builder.toString();
  }

  public String getDisplayStringFull() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);
    append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) append(builder, myClassifier);
    append(builder, myVersion);
    if (!StringUtil.isEmptyOrSpaces(myScope)) append(builder, myScope);

    return builder.toString();
  }

  public String getDisplayStringForLibraryName() {
    StringBuilder builder = new StringBuilder();

    append(builder, myGroupId);
    append(builder, myArtifactId);

    if (!StringUtil.isEmptyOrSpaces(myType) && !MavenConstants.TYPE_JAR.equals(myType)) append(builder, myType);
    if (!StringUtil.isEmptyOrSpaces(myClassifier)) append(builder, myClassifier);

    String version = !StringUtil.isEmptyOrSpaces(myBaseVersion) ? myBaseVersion : myVersion;
    if (!StringUtil.isEmptyOrSpaces(version)) append(builder, version);

    return builder.toString();
  }

  @Override
  public String toString() {
    return getDisplayStringFull();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MavenArtifact that = (MavenArtifact)o;

    if (myGroupId != null ? !myGroupId.equals(that.myGroupId) : that.myGroupId != null) return false;
    if (myArtifactId != null ? !myArtifactId.equals(that.myArtifactId) : that.myArtifactId != null) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    if (myBaseVersion != null ? !myBaseVersion.equals(that.myBaseVersion) : that.myBaseVersion != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;
    if (myClassifier != null ? !myClassifier.equals(that.myClassifier) : that.myClassifier != null) return false;
    if (myScope != null ? !myScope.equals(that.myScope) : that.myScope != null) return false;
    if (myExtension != null ? !myExtension.equals(that.myExtension) : that.myExtension != null) return false;
    if (myFile != null ? !myFile.equals(that.myFile) : that.myFile != null) return false;
    if (myTrail != null ? !myTrail.equals(that.myTrail) : that.myTrail != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId != null ? myGroupId.hashCode() : 0;
    result = 31 * result + (myArtifactId != null ? myArtifactId.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myBaseVersion != null ? myBaseVersion.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    result = 31 * result + (myClassifier != null ? myClassifier.hashCode() : 0);
    result = 31 * result + (myScope != null ? myScope.hashCode() : 0);
    result = 31 * result + (myExtension != null ? myExtension.hashCode() : 0);
    result = 31 * result + (myFile != null ? myFile.hashCode() : 0);
    result = 31 * result + (myTrail != null ? myTrail.hashCode() : 0);
    return result;
  }
}

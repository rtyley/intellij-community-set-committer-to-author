package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactPointerImpl implements ArtifactPointer {
  private final Project myProject;
  private String myName;
  private Artifact myArtifact;

  public ArtifactPointerImpl(@NotNull Project project, @NotNull String name) {
    myProject = project;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public Artifact getArtifact() {
    if (myArtifact == null) {
      myArtifact = findArtifact(ArtifactManager.getInstance(myProject));
    }
    return myArtifact;
  }

  public Artifact findArtifact(@NotNull ArtifactModel artifactModel) {
    if (myArtifact != null) {
      final Artifact artifact = artifactModel.getArtifactByOriginal(myArtifact);
      if (!artifact.equals(myArtifact)) {
        return artifact;
      }
    }
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(Artifact artifact) {
    myArtifact = artifact;
  }

  void setName(String name) {
    myName = name;
  }
}

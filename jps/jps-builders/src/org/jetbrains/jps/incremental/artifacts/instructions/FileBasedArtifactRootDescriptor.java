package org.jetbrains.jps.incremental.artifacts.instructions;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author nik
 */
public class FileBasedArtifactRootDescriptor extends ArtifactRootDescriptor {
  public FileBasedArtifactRootDescriptor(@NotNull File file,
                                         @NotNull SourceFileFilter filter,
                                         int index,
                                         ArtifactBuildTarget target,
                                         @NotNull DestinationInfo destinationInfo) {
    super(file, filter, index, target, destinationInfo);
  }

  @Override
  protected String getFullPath() {
    return myRoot.getPath();
  }

  public void copyFromRoot(String filePath,
                           int rootIndex, String outputPath,
                           CompileContext context, SourceToOutputMapping srcOutMapping,
                           ArtifactOutputToSourceMapping outSrcMapping) throws IOException {
    final File file = new File(FileUtil.toSystemDependentName(filePath));
    if (!file.exists()) return;
    String targetPath;
    if (!FileUtil.filesEqual(file, getRootFile())) {
      final String relativePath = FileUtil.getRelativePath(FileUtil.toSystemIndependentName(getRootFile().getPath()), filePath, '/');
      targetPath = JpsPathUtil.appendToPath(outputPath, relativePath);
    }
    else {
      targetPath = outputPath;
    }

    if (outSrcMapping.getState(targetPath) == null) {
      context.getLoggingManager().getArtifactBuilderLogger().fileCopied(filePath);
      final File targetFile = new File(FileUtil.toSystemDependentName(targetPath));
      FileUtil.copyContent(file, targetFile);
      srcOutMapping.appendOutput(filePath, targetPath);
    }
    outSrcMapping.appendData(targetPath, Collections.singletonList(new ArtifactOutputToSourceMapping.SourcePathAndRootIndex(filePath, rootIndex)));
  }
}

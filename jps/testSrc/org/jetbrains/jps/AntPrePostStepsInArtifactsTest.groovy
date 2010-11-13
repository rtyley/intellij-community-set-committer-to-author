package org.jetbrains.jps

import org.jetbrains.jps.util.FileUtil

class AntPrePostStepsInArtifactsTest extends JpsBuildTestCase {
  public void test() throws Exception {
    Project project = loadProject("testData/artifactWithAntPrePostTasks/.idea", [:])
    project.tempFolder = FileUtil.createTempDirectory("tmp").absolutePath
    project.clean()
    project.buildArtifact("main")
    project.deleteTempFiles()
    File outDir = new File("testData/artifactWithAntPrePostTasks/out");
    assertOutput(project, outDir.getAbsolutePath()) {
      dir("artifacts") {
        dir("main") {
          dir("dir") {
            file("file.txt")
          }
          file("prestep.txt")
          file("poststep.txt")
        }
      }
    }
  }
}

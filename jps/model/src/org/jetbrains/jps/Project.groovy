package org.jetbrains.jps
/**
 * @author max
 */
class Project {
  final CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
  final UiDesignerConfiguration uiDesignerConfiguration = new UiDesignerConfiguration()
  final IgnoredFilePatterns ignoredFilePatterns = new IgnoredFilePatterns()

  String projectCharset; // contains project charset, if not specified default charset will be used (used by compilers)
  Map<String, String> filePathToCharset = [:];

  def Project() {
  }

  def String toString() {
    return "Project"
  }
}

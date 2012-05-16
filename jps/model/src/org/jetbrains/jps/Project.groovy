package org.jetbrains.jps

import org.jetbrains.jps.artifacts.Artifact
import org.jetbrains.jps.idea.SdkLoader

/**
 * @author max
 */
class Project {
  String projectName
  int locationHash
  final Map<String, Library> globalLibraries = [:]
  final Map<String, Sdk> sdks = [:]

  Sdk projectSdk;
  final Map<String, Module> modules = [:]
  final Map<String, Library> libraries = [:]
  final Map<String, Artifact> artifacts = [:]
  final Map<String, RunConfiguration> runConfigurations = [:]
  final CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
  final UiDesignerConfiguration uiDesignerConfiguration = new UiDesignerConfiguration()
  final IgnoredFilePatterns ignoredFilePatterns = new IgnoredFilePatterns()

  String projectCharset; // contains project charset, if not specified default charset will be used (used by compilers)
  Map<String, String> filePathToCharset = [:];

  def Project() {
  }

  def Module createModule(String name, Closure initializer) {
    def module = new Module(this, name, initializer)
    modules.put(name, module)
    module
  }

  def Library createLibrary(String name, Closure initializer) {
    createLibrary(name, initializer, libraries, "project.library")
  }

  def Library createGlobalLibrary(String name, Closure initializer) {
    createLibrary(name, initializer, globalLibraries, "project.globalLibrary")
  }

  protected def Library createLibrary(String name, Closure initializer, Map<String, Library> libraries, String accessor) {
    Library lib = new Library(this, name)
    libraries.put(name, lib)
    lib
  }

  def JavaSdk createJavaSdk(String name, String path, Closure initializer) {
    def sdk = new JavaSdkImpl(this, name, null, path, initializer)
    sdks[name] = sdk
    return sdk
  }

  def Sdk createSdk(String typeName, String sdkName, String version, String path, Node additionalData) {
    def sdk = SdkLoader.createSdk(this, typeName, sdkName, version, path, additionalData)
    sdks[sdkName] = sdk
    return sdk
  }

  def String toString() {
    return "Project with ${modules.size()} modules and ${libraries.size()} libraries"
  }

  def ClasspathItem resolve(Object dep) {
    assert dep instanceof ClasspathItem : dep
    return dep
  }
}

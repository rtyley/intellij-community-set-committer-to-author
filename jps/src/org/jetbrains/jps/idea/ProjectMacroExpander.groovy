package org.jetbrains.jps.idea

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.MacroExpansion

/**
 * @author nik
 */
public class ProjectMacroExpander implements MacroExpander, MacroExpansion {
  private Map<String, String> pathVariables
  private String projectBasePath

  ProjectMacroExpander(Map<String, String> pathVariables, String projectBasePath) {
    this.pathVariables = pathVariables
    this.projectBasePath = projectBasePath
  }

  @Override
  String expandMacros(String path) {
    if (path == null) return path
    path = path.replace("\$PROJECT_DIR\$", projectBasePath)
    pathVariables.each { name, value ->
      path = path.replace("\$${name}\$", value)
    }
    return path
  }

  @Override
  String expandProjectMacro(String path) {
    return expandMacros(path)
  }


}

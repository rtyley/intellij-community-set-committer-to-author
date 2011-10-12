package org.jetbrains.jps.javaee;


import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.LayoutElement
import org.jetbrains.jps.artifacts.LayoutElementTypeService
import org.jetbrains.jps.artifacts.ModuleOutputElement
import org.jetbrains.jps.idea.ProjectLoadingErrorReporter

/**
 * @author nik
 */
class JavaeeFacetClassesElementType extends LayoutElementTypeService {
  JavaeeFacetClassesElementType() {
    super("javaee-facet-classes")
  }

  @Override
  LayoutElement createElement(Project project, Node tag, MacroExpander macroExpander, ProjectLoadingErrorReporter errorReporter) {
    String facetId = tag."@facet"
    return new ModuleOutputElement(moduleName: facetId.substring(0, facetId.indexOf('/')))
  }
}

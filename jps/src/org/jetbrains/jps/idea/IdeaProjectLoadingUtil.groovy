package org.jetbrains.jps.idea

import org.jetbrains.jps.Project

/**
 * @author nik
 */
public class IdeaProjectLoadingUtil {
  static String pathFromUrl(String url) {
    if (url == null) return null
    if (url.startsWith("file://")) {
      return url.substring("file://".length())
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length())
      if (url.endsWith("!/"))
        url = url.substring(0, url.length() - "!/".length())
    }
    url
  }

  static Facet findFacetById(Project project, String facetId) {
    def moduleName = facetId.substring(0, facetId.indexOf('/'))
    def facet = project.modules[moduleName]?.facets[facetId]
    if (facet == null) {
      project.error("Facet not found: id=$facetId")
    }
    return facet
  }
}

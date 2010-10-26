package org.jetbrains.jps.builders

import org.jetbrains.jps.ModuleBuildState
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.Project

/**
 * @author nik
 */
public interface ModuleCycleBuilder {
  def preprocessModuleCycle(ModuleBuildState state, ModuleChunk moduleChunk, Project project)
}
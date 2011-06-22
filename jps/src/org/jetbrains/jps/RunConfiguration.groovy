package org.jetbrains.jps

import org.jetbrains.jps.idea.IdeaProjectLoadingUtil
import org.jetbrains.jps.idea.ModuleMacroExpander
import org.jetbrains.jps.idea.OwnServiceLoader
import org.jetbrains.jps.runConf.RunConfigurationLauncherService

/**
 * Represents IntelliJ IDEA run configuration.
 * @author pavel.sher
 */
public class RunConfiguration {
  final Project project;
  final String name;
  final String type;
  final Module module;
  final String workingDir;
  final Map<String, String> allOptions;
  final Map<String, String> envVars;
  final Node node

  def RunConfiguration(Project project, MacroExpander macroExpander, Node confTag) {
    this.project = project;
    this.name = confTag.'@name';
    this.type = confTag.'@type';
    this.node = confTag;

    this.allOptions = [:];
    confTag.option.each{ opt ->
      def value = opt.'@value';
      if (value == null) {
        value = opt.value ? opt.value[0].'@defaultName' : null;
      }
      this.allOptions[opt.'@name'] = value;
    }

    def moduleNode = confTag.module[0];
    if (moduleNode != null && !"wholeProject".equals(this.allOptions['TEST_SEARCH_SCOPE'])) {
      this.module = project.modules[moduleNode.'@name'];
    } else {
      this.module = null;
    }

    if (this.module != null) {
      macroExpander = new ModuleMacroExpander(macroExpander, this.module.basePath);
    }

    def String workDirUrl = this.allOptions['WORKING_DIRECTORY'];
    if (workDirUrl == null) workDirUrl = "";
    if (workDirUrl != '') {
      workDirUrl = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(workDirUrl));
    }

    this.workingDir = workDirUrl == '' ? new File(".").getCanonicalPath() : new File(workDirUrl).getCanonicalPath();

    this.envVars = [:];
    confTag.envs.env.each{ el ->
      this.envVars[el.'@name'] = el.'@value';
    }
  }

  private static OwnServiceLoader<RunConfigurationLauncherService> runConfLauncherServices = OwnServiceLoader.load(RunConfigurationLauncherService.class)

  def RunConfigurationLauncherService getLauncher() {
    for (RunConfigurationLauncherService service: runConfLauncherServices.iterator()) {
      if (service.typeId == type) {
        return service;
      }
    }

    return null;
  }
}
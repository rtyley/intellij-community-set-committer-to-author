package org.jetbrains.plugins.gradle.config

import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Test

import javax.swing.*

import static org.junit.Assert.*

/**
 * @author Denis Zhdanov
 * @since 12/05/2012
 */
public class GradleConfigurableTest {
  
  static def VALID_GRADLE_HOME = "valid"
  static def INVALID_GRADLE_HOME = "invalid"
  static def VALID_LINKED_PATH_WITH_WRAPPER = "linked path with wrapper"
  
  def GradleConfigurable configurable
  def projectImpl
  Project defaultProject = {} as Project
  Project project
  def helper
  Map<Project, GradleSettings> settings = [:].withDefault { new GradleSettings() }

  @Before
  void setUp() {
    helper = [
      getSettings : { settings[it] },
      applySettings: {linkedProjectPath, gradleHomePath, preferLocalInstallationToWrapper, autoImport, serviceDirectoryPath, project -> },
      applyPreferLocalInstallationToWrapper: { preferLocalInstallationToWrapper, project -> },
      getGradleHome: { new File(VALID_GRADLE_HOME) },
      getDefaultProject: { defaultProject },
      isGradleSdkHome: { it.path == VALID_GRADLE_HOME },
      isGradleWrapperDefined: { it == VALID_LINKED_PATH_WITH_WRAPPER }
    ]
    projectImpl = [:]
    project = projectImpl as Project

    configurable = new GradleConfigurable(project, helper as GradleConfigurable.Helper)
  }

  @Test
  void "'use wrapper' is reset for valid project on importing"() {
    projectImpl.isDefault = { true }
    settings[project].linkedProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    settings[project].preferLocalInstallationToWrapper = false
    settings[project].gradleHome = VALID_GRADLE_HOME
    configurable.alwaysShowLinkedProjectControls = true
    configurable.reset()
    configurable.linkedGradleProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    
    assertTrue(configurable.useWrapperButton.selected)
  }
  
  @Test
  void "gradle home control is disabled if 'use wrapper' is selected initially"() {
    projectImpl.isDefault = { true }
    settings[project].linkedProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    settings[project].preferLocalInstallationToWrapper = false
    settings[project].gradleHome = INVALID_GRADLE_HOME
    configurable.alwaysShowLinkedProjectControls = true
    configurable.reset()
    configurable.linkedGradleProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    
    assertFalse(configurable.gradleHomePathField.enabled)
  }

  @SuppressWarnings("GroovyAssignabilityCheck")
  @Test
  void "invalid gradle home is not reported if home control is inactive"() {
    projectImpl.isDefault = { false }
    settings[project].linkedProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    settings[project].preferLocalInstallationToWrapper = false
    settings[project].gradleHome = INVALID_GRADLE_HOME
    helper.showBalloon = { messageType, settingType, long delay -> fail() }
    configurable.reset()

    assertFalse(configurable.gradleHomePathField.enabled)
    assertEquals(GradleHomeSettingType.EXPLICIT_INCORRECT, configurable.currentGradleHomeSettingType)
    def component = configurable.createComponent()
    component.firePropertyChange("ancestor", null, new JPanel()); // Emulate component initialization to allow balloon showing
    configurable.showBalloonIfNecessary()
    
    configurable.gradleHomePathField.enabled = true
    def shouldFail = true
    helper.showBalloon = { messageType, settingType, long delay -> shouldFail = false }
    configurable.showBalloonIfNecessary()
    if (shouldFail) {
      fail()
    }
  }

  @Test
  void "do not show 'invalid gradle path' balloon if 'use wrapper' is selected"() {
    settings[project].linkedProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    settings[project].preferLocalInstallationToWrapper = false
    configurable.useWrapperButton.selected = false
    configurable.gradleHomePathField.text = INVALID_GRADLE_HOME
    configurable.useWrapperButton.selected = true
    helper.showBalloon = { messageType, settingType, long delay -> fail() }
    configurable.apply()
  }

  @Test
  void "'use wrapper' is not automatically applied if a user selected 'use local installation' previously for non-default project"() {
    projectImpl.isDefault = { false }
    settings[project].linkedProjectPath = VALID_LINKED_PATH_WITH_WRAPPER
    settings[project].preferLocalInstallationToWrapper = true
    settings[project].gradleHome = VALID_GRADLE_HOME
    configurable.reset()
    
    assertFalse(configurable.useWrapperButton.selected)
  }
}

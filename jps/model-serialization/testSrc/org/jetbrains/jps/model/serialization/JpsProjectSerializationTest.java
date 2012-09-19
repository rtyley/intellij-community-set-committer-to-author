package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsProjectSerializationTest extends JpsSerializationTestCase {
  public static final String SAMPLE_PROJECT_PATH = "/jps/model-serialization/testData/sampleProject";

  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsModule> modules = myProject.getModules();
    assertEquals(3, modules.size());
    JpsModule main = modules.get(0);
    assertEquals("main", main.getName());
    JpsModule util = modules.get(1);
    assertEquals("util", util.getName());
    JpsModule xxx = modules.get(2);
    assertEquals("xxx", xxx.getName());

    List<JpsLibrary> libraries = myProject.getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());

    List<JpsDependencyElement> dependencies = util.getDependenciesList().getDependencies();
    assertEquals(4, dependencies.size());
    JpsSdkDependency sdkDependency = assertInstanceOf(dependencies.get(0), JpsSdkDependency.class);
    assertSame(JpsJavaSdkType.INSTANCE, sdkDependency.getSdkType());
    JpsSdkReference<?> reference = sdkDependency.getSdkReference();
    assertNotNull(reference);
    assertEquals("1.5", reference.getSdkName());
    assertInstanceOf(dependencies.get(1), JpsModuleSourceDependency.class);
    assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class);
    assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class);

    JpsSdkDependency inheritedSdkDependency = assertInstanceOf(main.getDependenciesList().getDependencies().get(0), JpsSdkDependency.class);
    JpsSdkReference<?> projectSdkReference = inheritedSdkDependency.getSdkReference();
    assertNotNull(projectSdkReference);
    assertEquals("1.6", projectSdkReference.getSdkName());

    assertEquals(getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, true));
    assertEquals(getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, false));
  }

  public void testLoadEncoding() {
    loadProject(SAMPLE_PROJECT_PATH);
    JpsEncodingConfigurationService service = JpsEncodingConfigurationService.getInstance();
    assertEquals("UTF-8", service.getProjectEncoding(myModel));
    JpsEncodingProjectConfiguration configuration = service.getEncodingConfiguration(myProject);
    assertNotNull(configuration);
    assertEquals("UTF-8", configuration.getProjectEncoding());
    assertEquals("windows-1251", configuration.getEncoding(getUrl("util")));
  }

  public void testSaveProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsModule> modules = myProject.getModules();
    doTestSaveModule(modules.get(0), "main.iml");
    doTestSaveModule(modules.get(1), "util/util.iml");
    //tod[nik] remember that test output root wasn't specified and doesn't save it to avoid unnecessary modifications of iml files
    //doTestSaveModule(modules.get(2), "xxx/xxx.iml");

    File[] libs = getFileInSampleProject(".idea/libraries").listFiles();
    assertNotNull(libs);
    for (File libFile : libs) {
      String libName = FileUtil.getNameWithoutExtension(libFile);
      JpsLibrary library = myProject.getLibraryCollection().findLibrary(libName);
      assertNotNull(libName, library);
      doTestSaveLibrary(libFile, libName, library);
    }
  }

  private void doTestSaveLibrary(File libFile, String libName, JpsLibrary library) {
    try {
      Element actual = new Element("library");
      JpsLibraryTableSerializer.saveLibrary(library, actual, libName);
      JpsMacroExpander
        macroExpander = JpsProjectLoader.createProjectMacroExpander(Collections.<String, String>emptyMap(), getFileInSampleProject(""));
      Element rootElement = JpsLoaderBase.loadRootElement(libFile, macroExpander);
      Element expected = rootElement.getChild("library");
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doTestSaveModule(JpsModule module, final String moduleFilePath) {
    try {
      Element actual = JDomSerializationUtil.createComponentElement("NewModuleRootManager");
      JpsModuleRootModelSerializer.saveRootModel(module, actual);
      File imlFile = getFileInSampleProject(moduleFilePath);
      Element rootElement = loadModuleRootTag(imlFile);
      Element expected = JDomSerializationUtil.findComponent(rootElement, "NewModuleRootManager");
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public File getFileInSampleProject(String relativePath) {
    return new File(getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH + "/" + relativePath));
  }

  public void testLoadIdeaProject() {
    long start = System.currentTimeMillis();
    loadProjectByAbsolutePath(PathManager.getHomePath());
    assertTrue(myProject.getModules().size() > 0);
    System.out.println("JpsProjectSerializationTest: " + myProject.getModules().size() + " modules, " + myProject.getLibraryCollection().getLibraries().size() + " libraries and " +
                       JpsArtifactService.getInstance().getArtifacts(myProject).size() + " artifacts loaded in " + (System.currentTimeMillis() - start) + "ms");
  }
}

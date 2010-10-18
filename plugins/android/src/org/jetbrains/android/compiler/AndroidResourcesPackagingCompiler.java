package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesPackagingCompiler implements ClassPostProcessingCompiler {
  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    Module[] affectedModules = context.getCompileScope().getAffectedModules();
    for (Module module : affectedModules) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFile(module);
        VirtualFile assetsDir = AndroidRootUtil.getAssetsDir(module);
        if (manifestFile != null) {
          AndroidFacetConfiguration configuration = facet.getConfiguration();
          VirtualFile outputDir = context.getModuleOutputDirectory(module);
          if (outputDir != null) {
            String outputPath = getOutputPath(module, outputDir);
            IAndroidTarget target = configuration.getAndroidTarget();
            if (target != null) {
              String assetsDirPath = assetsDir != null ? assetsDir.getPath() : null;
              String[] resourcesDirPaths = AndroidCompileUtil.collectResourceDirs(facet);
              items.add(new MyItem(module, target, manifestFile, resourcesDirPaths, assetsDirPath, outputPath));
            }
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
  }

  static String getOutputPath(Module module, VirtualFile outputDir) {
    return new File(outputDir.getPath(), module.getName() + ".apk.res").getPath();
  }

  @Override
  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    context.getProgressIndicator().setText("Packaging Android resources...");
    final List<ProcessingItem> result = new ArrayList<ProcessingItem>();
    for (ProcessingItem processingItem : items) {
      MyItem item = (MyItem)processingItem;
      try {
        Map<CompilerMessageCategory, List<String>> messages = AndroidApt.packageResources(item.myAndroidTarget,
                                                                                          item.myManifestFile.getPath(),
                                                                                          item.myResourceDirPaths, item.myAssetsDirPath,
                                                                                          item.myOutputPath);
        AndroidCompileUtil.addMessages(context, messages);
      }
      catch (final IOException e) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            if (context.getProject().isDisposed()) return;
            context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          }
        });
      }
      if (context.getMessages(CompilerMessageCategory.ERROR).length == 0) {
        result.add(item);
      }
    }
    return result.toArray(new ProcessingItem[result.size()]);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Resources Packaging Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new ResourcesValidityState(in);
  }

  private static class MyItem implements ProcessingItem {
    final Module myModule;
    final VirtualFile myManifestFile;
    final IAndroidTarget myAndroidTarget;
    final String[] myResourceDirPaths;
    final String myAssetsDirPath;
    final String myOutputPath;

    private MyItem(Module module,
                   IAndroidTarget androidTarget,
                   VirtualFile manifestFile,
                   String[] resourceDirPaths,
                   String assetsDirPath,
                   String outputPath) {
      myModule = module;
      myAndroidTarget = androidTarget;
      myManifestFile = manifestFile;
      myResourceDirPaths = resourceDirPaths;
      myAssetsDirPath = assetsDirPath;
      myOutputPath = outputPath;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myManifestFile;
    }

    @Override
    public ValidityState getValidityState() {
      return new ResourcesValidityState(myModule);
    }
  }
}

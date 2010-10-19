package org.jetbrains.android.compiler.tools;

import com.android.jarutils.DebugKeyProvider;
import com.android.jarutils.JavaResourceFilter;
import com.android.jarutils.SignedJarBuilder;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

import static com.intellij.openapi.compiler.CompilerMessageCategory.ERROR;
import static com.intellij.openapi.compiler.CompilerMessageCategory.INFORMATION;

/**
 * @author yole
 */
public class AndroidApkBuilder {
  private static final SignedJarBuilder.IZipEntryFilter myJavaResourcesFilter = new JavaResourceFilter();
  private static final String UNALIGNED_SUFFIX = ".unaligned";
  private static final String UNSIGNED_SUFFIX = ".unsigned";

  private AndroidApkBuilder() {
  }

  private static Map<CompilerMessageCategory, List<String>> filterUsingKeystoreMessages(Map<CompilerMessageCategory, List<String>> messages) {
    List<String> infoMessages = messages.get(INFORMATION);
    if (infoMessages == null) {
      infoMessages = new ArrayList<String>();
      messages.put(INFORMATION, infoMessages);
    }
    final List<String> errors = messages.get(ERROR);
    for (Iterator<String> iterator = errors.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      if (s.startsWith("Using keystore:")) {
        // not actually an error
        infoMessages.add(s);
        iterator.remove();
      }
    }
    return messages;
  }

  public static Map<CompilerMessageCategory, List<String>> execute(@NotNull String sdkPath,
                                                                   @NotNull String resPackagePath,
                                                                   @NotNull String dexPath,
                                                                   @NotNull VirtualFile[] sourceRoots,
                                                                   @NotNull String[] externalJars,
                                                                   @NotNull VirtualFile[] nativeLibsFolders,
                                                                   @NotNull String finalApk,
                                                                   boolean generateUnsignedApk) throws IOException {
    String unsignedApk = finalApk + UNSIGNED_SUFFIX;

    Map<CompilerMessageCategory, List<String>> map;
    if (generateUnsignedApk) {
      map = filterUsingKeystoreMessages(
        finalPackage(resPackagePath, dexPath, sourceRoots, externalJars, nativeLibsFolders, unsignedApk, false));
    }
    else {
      map = new HashMap<CompilerMessageCategory, List<String>>();
    }

    final String zipAlignPath = sdkPath + File.separator + AndroidUtils.toolPath(SdkConstants.FN_ZIPALIGN);
    boolean withAlignment = new File(zipAlignPath).exists();
    String unalignedApk = finalApk + UNALIGNED_SUFFIX;

    Map<CompilerMessageCategory, List<String>> map2 = filterUsingKeystoreMessages(
      finalPackage(resPackagePath, dexPath, sourceRoots, externalJars, nativeLibsFolders, withAlignment ? unalignedApk : finalApk, true));
    map.putAll(map2);

    if (withAlignment && map.get(ERROR).size() == 0) {
      map2 = ExecutionUtil.execute(zipAlignPath, "-f", "4", unalignedApk, finalApk);
      map.putAll(map2);
    }
    return map;
  }

  private static Map<CompilerMessageCategory, List<String>> finalPackage(@NotNull String apkPath,
                                                                         @NotNull String dexPath,
                                                                         @NotNull VirtualFile[] sourceRoots,
                                                                         @NotNull String[] externalJars,
                                                                         @NotNull VirtualFile[] nativeLibsFolders,
                                                                         @NotNull String outputApk,
                                                                         boolean signed) {
    final Map<CompilerMessageCategory, List<String>> result = new HashMap<CompilerMessageCategory, List<String>>();
    result.put(ERROR, new ArrayList<String>());
    result.put(INFORMATION, new ArrayList<String>());
    FileOutputStream fos = null;
    try {
      String osKeyPath = DebugKeyProvider.getDefaultKeyStoreOsPath();

      DebugKeyProvider provider = new DebugKeyProvider(osKeyPath, null, new DebugKeyProvider.IKeyGenOutput() {
        public void err(String message) {
          result.get(ERROR).add("Error during key creation: " + message);
        }

        public void out(String message) {
          result.get(INFORMATION).add("Info message during key creation: " + message);
        }
      });
      PrivateKey key = provider.getDebugKey();
      X509Certificate certificate = signed ? (X509Certificate)provider.getCertificate() : null;

      if (key == null) {
        result.get(ERROR).add(AndroidBundle.message("android.cannot.create.new.key.error"));
        return result;
      }

      if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
        String date = DateFormatUtil.formatPrettyDateTime(certificate.getNotAfter());
        result.get(ERROR).add(AndroidBundle.message("android.debug.certificate.expired.error", date));
        return result;
      }

      fos = new FileOutputStream(outputApk);
      SignedJarBuilder builder = new SignedJarBuilder(fos, key, certificate);
      FileInputStream fis = new FileInputStream(apkPath);
      try {
        builder.writeZip(fis, null);
      }
      finally {
        fis.close();
      }
      File entryFile = new File(dexPath);
      builder.writeFile(entryFile, AndroidUtils.CLASSES_FILE_NAME);
      for (VirtualFile sourceRoot : sourceRoots) {
        writeStandardSourceFolderResources(builder, sourceRoot, sourceRoot, new HashSet<VirtualFile>(), new HashSet<String>());
      }

      for (String externalJar : externalJars) {
        try {
          fis = new FileInputStream(externalJar);
          builder.writeZip(fis, myJavaResourcesFilter);
        }
        finally {
          fis.close();
        }
      }
      for (VirtualFile nativeLibsFolder : nativeLibsFolders) {
        for (VirtualFile child : nativeLibsFolder.getChildren()) {
          writeNativeLibraries(builder, nativeLibsFolder, child);
        }
      }
      builder.close();
    }
    catch (IOException e) {
      return addExceptionMessage(e, result);
    }
    catch (CertificateException e) {
      return addExceptionMessage(e, result);
    }
    catch (DebugKeyProvider.KeytoolException e) {
      return addExceptionMessage(e, result);
    }
    catch (AndroidLocation.AndroidLocationException e) {
      return addExceptionMessage(e, result);
    }
    catch (NoSuchAlgorithmException e) {
      return addExceptionMessage(e, result);
    }
    catch (UnrecoverableEntryException e) {
      return addExceptionMessage(e, result);
    }
    catch (KeyStoreException e) {
      return addExceptionMessage(e, result);
    }
    catch (GeneralSecurityException e) {
      return addExceptionMessage(e, result);
    }
    finally {
      if (fos != null) {
        try {
          fos.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    return result;
  }

  private static void writeNativeLibraries(SignedJarBuilder builder, VirtualFile nativeLibsFolder, VirtualFile child) throws IOException {
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
    collectNativeLibraries(nativeLibsFolder, child, list);
    String relativePath = VfsUtil.getRelativePath(child, nativeLibsFolder, File.separatorChar);
    String libsDirPathInApk = FileUtil.toSystemIndependentName(SdkConstants.FD_APK_NATIVE_LIBS + File.separator + relativePath);
    for (VirtualFile file : list) {
      builder.writeFile(toIoFile(file), libsDirPathInApk);
    }
  }

  private static Map<CompilerMessageCategory, List<String>> addExceptionMessage(Exception e,
                                                                                Map<CompilerMessageCategory, List<String>> result) {
    String simpleExceptionName = e.getClass().getCanonicalName();
    result.get(ERROR).add(simpleExceptionName + ": " + e.getMessage());
    return result;
  }

  public static void collectNativeLibraries(@NotNull VirtualFile libsDir, @NotNull VirtualFile file, @NotNull List<VirtualFile> result) {
    if (!file.isDirectory()) {
      String ext = file.getExtension();
      if (AndroidUtils.EXT_NATIVE_LIB.equalsIgnoreCase(ext)) {
        result.add(file);
      }
    }
    else if (JavaResourceFilter.checkFolderForPackaging(file.getName())) {
      for (VirtualFile child : file.getChildren()) {
        collectNativeLibraries(libsDir, child, result);
      }
    }
  }

  private static void writeStandardSourceFolderResources(SignedJarBuilder jarBuilder,
                                                         @NotNull VirtualFile sourceRoot,
                                                         @NotNull VirtualFile sourceFolder,
                                                         @NotNull Set<VirtualFile> visited,
                                                         @NotNull Set<String> added) throws IOException {
    visited.add(sourceFolder);
    for (VirtualFile child : sourceFolder.getChildren()) {
      if (child.exists()) {
        if (child.isDirectory()) {
          if (!visited.contains(child) && JavaResourceFilter.checkFolderForPackaging(child.getName())) {
            writeStandardSourceFolderResources(jarBuilder, sourceRoot, child, visited, added);
          }
        }
        else if (checkFileForPackaging(child)) {
          String relativePath = FileUtil.toSystemIndependentName(VfsUtil.getRelativePath(child, sourceRoot, File.separatorChar));
          if (relativePath != null && !added.contains(relativePath)) {
            File file = toIoFile(child);
            jarBuilder.writeFile(file, FileUtil.toSystemIndependentName(relativePath));
            added.add(relativePath);
          }
        }
      }
    }
  }

  private static File toIoFile(VirtualFile child) {
    return new File(FileUtil.toSystemDependentName(child.getPath())).getAbsoluteFile();
  }

  private static boolean checkFileForPackaging(VirtualFile file) {
    String fileName = file.getNameWithoutExtension();
    if (fileName.length() > 0) {
      return JavaResourceFilter.checkFileForPackaging(fileName, file.getExtension());
    }
    return false;
  }
}
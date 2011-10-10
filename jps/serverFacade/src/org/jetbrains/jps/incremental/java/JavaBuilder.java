package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/21/11
 */
public class JavaBuilder extends Builder{
  public static final String BUILDER_NAME = "java";
  public static final String JAVA_EXTENSION = ".java";

  private static final FileFilter JAVA_SOURCES_FILTER = new FileFilter() {
    public boolean accept(File file) {
      return file.getName().endsWith(JAVA_EXTENSION);
    }
  };
  private static final String JAVAC_COMPILER_NAME = "javac";
  private final EmbeddedJavac myJavacCompiler;

  public JavaBuilder(ExecutorService tasksExecutor) {
    myJavacCompiler = new EmbeddedJavac(tasksExecutor);
    // add here class processors in the sequence they should be executed
    //myJavacCompiler.addClassProcessor(new EmbeddedJavac.ClassPostProcessor() {
    //  public void process(CompileContext context, OutputFileObject out) {
    //  }
    //});
  }

  public ExitCode build(final CompileContext context, final ModuleChunk chunk) throws ProjectBuildException {
    try {
      final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);

      final List<File> files = new ArrayList<File>();
      context.processFiles(chunk, new FileProcessor() {
        public boolean apply(Module module, File file, String sourceRoot) throws Exception {
          if (JAVA_SOURCES_FILTER.accept(file)) {
            if (!context.isMake() || tsStorage.getStamp(file) != file.lastModified()) {
              files.add(file);
            }
          }
          return true;
        }
      });

      return compile(context, chunk, files);
    }
    catch (Exception e) {
      throw new ProjectBuildException(e.getMessage(), e);
    }
  }

  private ExitCode compile(final CompileContext context, ModuleChunk chunk, List<File> files) throws Exception {
    if (files.isEmpty()) {
      return ExitCode.OK;
    }

    ProjectPaths paths = ProjectPaths.KEY.get(context);
    if (paths == null) {
      ProjectPaths.KEY.set(context, paths = new ProjectPaths(context.getProject()));
    }

    final Collection<File> classpath = paths.getCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake());
    final Collection<File> platformCp = paths.getPlatformCompilationClasspath(chunk, context.isCompilingTests(), !context.isMake());
    final Map<File, Set<File>> outs = buildOutputDirectoriesMap(context, chunk);
    final List<String> options = getCompilationOptions(context, chunk);

    final int ERROR = 0, WARNING = 1;
    final int[] statistics = new int[] {0, 0};

    final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(BUILDER_NAME);
    final Set<File> successfullyCompiled = new HashSet<File>();
    final boolean compilationOk = myJavacCompiler.compile(options, files, classpath, platformCp, outs, context, new EmbeddedJavac.OutputConsumer() {
      public void classFileWritten(OutputFileObject output) {
        context.processMessage(new ProgressMessage("Compiled " + output.getFile().getPath()));
        final JavaFileObject source = output.getSource();
        if (source != null) {
          final File file = new File(source.toUri());
          synchronized (successfullyCompiled) {
            successfullyCompiled.add(file);
          }
        }
      }

      public void outputLineAvailable(String line) {
        context.processMessage(new CompilerMessage(JAVAC_COMPILER_NAME, BuildMessage.Kind.INFO, line));
      }

      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        final CompilerMessage.Kind kind;
        switch (diagnostic.getKind()) {
          case ERROR:
            kind = BuildMessage.Kind.ERROR;
            statistics[ERROR]++;
            break;
          case MANDATORY_WARNING:
          case WARNING:
            kind = BuildMessage.Kind.WARNING;
            statistics[WARNING]++;
            break;
          default:
            kind = BuildMessage.Kind.INFO;
        }
        final String srcPath;
        final JavaFileObject source = diagnostic.getSource();
        if (source != null) {
          srcPath = FileUtil.toSystemIndependentName(new File(source.toUri()).getPath());
        }
        else {
          srcPath = null;
        }
        context.processMessage(new CompilerMessage(
          JAVAC_COMPILER_NAME, kind, diagnostic.getMessage(Locale.US), srcPath,
          diagnostic.getStartPosition(), diagnostic.getEndPosition(), diagnostic.getPosition(),
          diagnostic.getLineNumber(), diagnostic.getColumnNumber()
        ));
      }
    });

    for (File file : successfullyCompiled) {
      tsStorage.saveStamp(file);
    }

    if (!compilationOk || statistics[ERROR] > 0) {
      throw new ProjectBuildException("Compilation failed: errors: " + statistics[ERROR] + "; warnings: " + statistics[WARNING]);
    }

    return ExitCode.OK;
  }

  public String getDescription() {
    return "Java Builder";
  }

  private static List<String> getCompilationOptions(CompileContext context, ModuleChunk chunk) {
    return Collections.emptyList();
  }

  private static Map<File, Set<File>> buildOutputDirectoriesMap(CompileContext context, ModuleChunk chunk) {
    final Map<File, Set<File>> map = new HashMap<File, Set<File>>();
    final boolean compilingTests = context.isCompilingTests();
    for (Module module : chunk.getModules()) {
      final String outputPath;
      final Collection<String> srcPaths;
      if (compilingTests) {
        outputPath = module.getTestOutputPath();
        srcPaths = (Collection<String>)module.getTestRoots();
      }
      else {
        outputPath = module.getOutputPath();
        srcPaths = (Collection<String>)module.getSourceRoots();
      }
      final Set<File> roots = new HashSet<File>();
      for (String path : srcPaths) {
        roots.add(new File(path));
      }
      map.put(new File(outputPath), roots);
    }
    return map;
  }
}

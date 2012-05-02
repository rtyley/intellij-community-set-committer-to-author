package org.jetbrains.ether.dependencyView;

import org.jetbrains.asm4.ClassReader;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 15:55
 * To change this template use File | Settings | File Templates.
 */
public class Callbacks {

  public interface Backend {
    void associate(String classFileName, String sourceFileName, ClassReader cr);
    void registerImports(String className, Collection<String> imports, Collection<String> staticImports);
  }

  public static class ConstantAffection {
    private final boolean myKnown;
    private final Collection<File> myAffectedFiles;

    public ConstantAffection(final Collection<File> affectedFiles) {
      myAffectedFiles = affectedFiles;
      myKnown = true;
    }

    public ConstantAffection() {
      myKnown = false;
      myAffectedFiles = null;
    }

    public boolean isKnown(){
      return myKnown;
    }

    public Collection<File> getAffectedFiles (){
      return myAffectedFiles;
    }
  }

  public interface ConstantAffectionResolver {
    Future<ConstantAffection> request(final String owner, final String name);
  }
}

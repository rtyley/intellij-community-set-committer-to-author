package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IntInlineKeyDescriptor;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntProcedure;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.Opcodes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 28.01.11
 * Time: 16:20
 * To change this template use File | Settings | File Templates.
 */
public class Mappings {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.ether.dependencyView.Mappings");

  private final static String CLASS_TO_SUBCLASSES = "classToSubclasses.tab";
  private final static String CLASS_TO_CLASS = "classToClass.tab";
  private final static String SOURCE_TO_CLASS = "sourceToClass.tab";
  private final static String SOURCE_TO_ANNOTATIONS = "sourceToAnnotations.tab";
  private final static String SOURCE_TO_USAGES = "sourceToUsages.tab";
  private final static String CLASS_TO_SOURCE = "classToSource.tab";
  private static final IntInlineKeyDescriptor INT_KEY_DESCRIPTOR = new IntInlineKeyDescriptor();
  private static final int DEFAULT_SET_CAPACITY = 32;
  private static final float DEFAULT_SET_LOAD_FACTOR = 0.98f;
  private static final CollectionFactory<ClassRepr> ourClassSetConstructor = new CollectionFactory<ClassRepr>() {
    public Set<ClassRepr> create() {
      return new HashSet<ClassRepr>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    }
  };

  private final boolean myIsDelta;
  private final boolean myDeltaIsTransient;
  private boolean myIsDifferentiated = false;
  private boolean myIsRebuild = false;

  private final TIntHashSet myChangedClasses;
  private final TIntHashSet myChangedFiles;
  private final Set<ClassRepr> myDeletedClasses;
  private final Object myLock;
  private final File myRootDir;

  private DependencyContext myContext;
  private final int myInitName;
  private final int myEmptyName;
  private org.jetbrains.ether.dependencyView.Logger<Integer> myDebugS;

  private IntIntMultiMaplet myClassToSubclasses;
  private IntIntMultiMaplet myClassToClassDependency;
  private IntObjectMultiMaplet<ClassRepr> mySourceFileToClasses;
  private IntIntMaplet myClassToSourceFile;

  private IntIntTransientMultiMaplet myRemovedSuperClasses;
  private IntIntTransientMultiMaplet myAddedSuperClasses;

  private Collection<String> myRemovedFiles;

  private Mappings(final Mappings base) throws IOException {
    myLock = base.myLock;
    myIsDelta = true;
    myPostPasses = new LinkedList<PostPass>();
    myChangedClasses = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myChangedFiles = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myDeletedClasses = new HashSet<ClassRepr>(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
    myDeltaIsTransient = base.myDeltaIsTransient;
    myRootDir = new File(FileUtil.toSystemIndependentName(base.myRootDir.getAbsolutePath()) + File.separatorChar + "myDelta");
    myContext = base.myContext;
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
    myDebugS = base.myDebugS;
    myRootDir.mkdirs();
    createImplementation();
  }

  public Mappings(final File rootDir, final boolean transientDelta) throws IOException {
    myLock = new Object();
    myIsDelta = false;
    myPostPasses = new LinkedList<PostPass>();
    myChangedClasses = null;
    myChangedFiles = null;
    myDeletedClasses = null;
    myDeltaIsTransient = transientDelta;
    myRootDir = rootDir;
    createImplementation();
    myInitName = myContext.get("<init>");
    myEmptyName = myContext.get("");
  }

  private void createImplementation() throws IOException {
    if (!myIsDelta) {
      myContext = new DependencyContext(myRootDir);
      myDebugS = myContext.getLogger(LOG);
    }

    myRemovedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;
    myAddedSuperClasses = myIsDelta ? new IntIntTransientMultiMaplet() : null;

    if (myIsDelta && myDeltaIsTransient) {
      myClassToSubclasses = new IntIntTransientMultiMaplet();
      myClassToClassDependency = new IntIntTransientMultiMaplet();
      mySourceFileToClasses = new IntObjectTransientMultiMaplet<ClassRepr>(ourClassSetConstructor);
      myClassToSourceFile = new IntIntTransientMaplet();
    }
    else {
      myClassToSubclasses = new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_SUBCLASSES), INT_KEY_DESCRIPTOR);
      myClassToClassDependency = new IntIntPersistentMultiMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_CLASS), INT_KEY_DESCRIPTOR);
      mySourceFileToClasses = new IntObjectPersistentMultiMaplet<ClassRepr>(
        DependencyContext.getTableFile(myRootDir, SOURCE_TO_CLASS), INT_KEY_DESCRIPTOR, ClassRepr.externalizer(myContext),
        ourClassSetConstructor
      );
      myClassToSourceFile = new IntIntPersistentMaplet(DependencyContext.getTableFile(myRootDir, CLASS_TO_SOURCE), INT_KEY_DESCRIPTOR);
    }
  }

  public Mappings createDelta() {
    synchronized (myLock) {
      try {
        return new Mappings(this);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void compensateRemovedContent(final Collection<File> compiled) {
    if (compiled != null) {
      for (final File file : compiled) {
        final int fileName = myContext.get(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
        if (!mySourceFileToClasses.containsKey(fileName)) {
          mySourceFileToClasses.put(fileName, new HashSet<ClassRepr>());
        }
      }
    }
  }

  @Nullable
  private ClassRepr getReprByName(final int name) {
    final int source = myClassToSourceFile.get(name);

    if (source > 0) {
      final Collection<ClassRepr> reprs = mySourceFileToClasses.get(source);

      if (reprs != null) {
        for (ClassRepr repr : reprs) {
          if (repr.myName == name) {
            return repr;
          }
        }
      }
    }

    return null;
  }

  public void clean() throws IOException {
    if (myRootDir != null) {
      synchronized (myLock) {
        close();
        FileUtil.delete(myRootDir);
        createImplementation();
      }
    }
  }

  public IntIntTransientMultiMaplet getRemovedSuperClasses() {
    return myRemovedSuperClasses;
  }

  public IntIntTransientMultiMaplet getAddedSuperClasses() {
    return myAddedSuperClasses;
  }

  private static class Option<X> {
    static final Option<Boolean> TRUE = new Option<Boolean>(Boolean.TRUE);
    static final Option<Boolean> FALSE = new Option<Boolean>(Boolean.FALSE);
    static final Option<Boolean> UNKNOWN = new Option<Boolean>();

    private final X myValue;

    Option(final X value) {
      this.myValue = value;
    }

    Option() {
      myValue = null;
    }

    public boolean isDefined() {
      return myValue != null;
    }

    public X value() {
      return myValue;
    }
  }

  private abstract class PostPass {
    boolean myPerformed = false;

    abstract void perform();

    void run() {
      if (!myPerformed) {
        myPerformed = true;
        perform();
      }
    }
  }

  private final List<PostPass> myPostPasses;

  private void addPostPass(final PostPass p) {
    myPostPasses.add(p);
  }

  private void runPostPasses() {
    final Set<ClassRepr> deleted = myDeletedClasses;
    if (deleted != null) {
      for (ClassRepr repr : deleted) {
        myChangedClasses.remove(repr.myName);
      }
    }

    for (final PostPass p : myPostPasses) {
      p.run();
    }
  }

  private static ClassRepr myMockClass = null;
  private static MethodRepr myMockMethod = null;

  private class Util {
    final Mappings myDelta;

    private Util() {
      myDelta = null;
    }

    private Util(Mappings delta) {
      this.myDelta = delta;
    }

    void appendDependents(final ClassRepr c, final TIntHashSet result) {
      final TIntHashSet depClasses = myDelta.myClassToClassDependency.get(c.myName);

      if (depClasses != null) {
        addAll(result, depClasses);
      }
    }

    void propagateMemberAccessRec(final TIntHashSet acc, final boolean isField, final boolean root, final int name, final int reflcass) {
      final ClassRepr repr = reprByName(reflcass);

      if (repr != null) {
        if (!root) {
          final Collection members = isField ? repr.getFields() : repr.getMethods();

          for (Object o : members) {
            final ProtoMember m = (ProtoMember)o;

            if (m.myName == name) {
              return;
            }
          }

          acc.add(reflcass);
        }

        final TIntHashSet subclasses = myClassToSubclasses.get(reflcass);

        if (subclasses != null) {
          subclasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subclass) {
              propagateMemberAccessRec(acc, isField, false, name, subclass);
              return true;
            }
          });
        }
      }
    }

    TIntHashSet propagateMemberAccess(final boolean isField, final int name, final int className) {
      final TIntHashSet acc = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

      propagateMemberAccessRec(acc, isField, true, name, className);

      return acc;
    }

    TIntHashSet propagateFieldAccess(final int name, final int className) {
      return propagateMemberAccess(true, name, className);
    }

    TIntHashSet propagateMethodAccess(final int name, final int className) {
      return propagateMemberAccess(false, name, className);
    }

    MethodRepr.Predicate lessSpecific(final MethodRepr than) {
      return new MethodRepr.Predicate() {
        @Override
        public boolean satisfy(final MethodRepr m) {
          if (m.myName == myInitName || m.myName != than.myName || m.myArgumentTypes.length != than.myArgumentTypes.length) {
            return false;
          }

          for (int i = 0; i < than.myArgumentTypes.length; i++) {
            final Option<Boolean> subtypeOf = isSubtypeOf(than.myArgumentTypes[i], m.myArgumentTypes[i]);
            if (subtypeOf.isDefined() && !subtypeOf.value()) {
              return false;
            }
          }

          return true;
        }
      };
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridingMethods(final MethodRepr m, final ClassRepr c, final boolean bySpecificity) {
      final Set<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      final MethodRepr.Predicate predicate = bySpecificity ? lessSpecific(m) : MethodRepr.equalByJavaRules(m);

      new Object() {
        public void run(final ClassRepr c) {
          final TIntHashSet subClasses = myClassToSubclasses.get(c.myName);

          if (subClasses != null) {
            subClasses.forEach(new TIntProcedure() {
              @Override
              public boolean execute(int subClassName) {
                final ClassRepr r = reprByName(subClassName);

                if (r != null) {
                  boolean cont = true;

                  final Collection<MethodRepr> methods = r.findMethods(predicate);

                  for (MethodRepr mm : methods) {
                    if (isVisibleIn(c, m, r)) {
                      result.add(new Pair<MethodRepr, ClassRepr>(mm, r));
                      cont = false;
                    }
                  }

                  if (cont) {
                    run(r);
                  }
                }
                return true;
              }
            });
          }
        }
      }.run(c);

      return result;
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridenMethods(final MethodRepr m, final ClassRepr c) {
      return findOverridenMethods(m, c, false);
    }

    Collection<Pair<MethodRepr, ClassRepr>> findOverridenMethods(final MethodRepr m, final ClassRepr c, final boolean bySpecificity) {
      final Set<Pair<MethodRepr, ClassRepr>> result = new HashSet<Pair<MethodRepr, ClassRepr>>();
      final MethodRepr.Predicate predicate = bySpecificity ? lessSpecific(m) : MethodRepr.equalByJavaRules(m);

      new Object() {
        public void run(final ClassRepr c) {
          final int[] supers = c.getSupers();

          for (int succName : supers) {
            final ClassRepr r = reprByName(succName);

            if (r != null) {
              boolean cont = true;

              final Collection<MethodRepr> methods = r.findMethods(predicate);

              for (MethodRepr mm : methods) {
                if (isVisibleIn(r, mm, c)) {
                  result.add(new Pair<MethodRepr, ClassRepr>(mm, r));
                  cont = false;
                }
              }

              if (cont) {
                run(r);
              }
            }
            else {
              result.add(new Pair<MethodRepr, ClassRepr>(myMockMethod, myMockClass));
            }
          }
        }
      }.run(c);

      return result;
    }

    Collection<Pair<MethodRepr, ClassRepr>> findAllMethodsBySpecificity(final MethodRepr m, final ClassRepr c) {
      final Collection<Pair<MethodRepr, ClassRepr>> result = findOverridenMethods(m, c, true);

      result.addAll(findOverridingMethods(m, c, true));

      return result;
    }

    Collection<Pair<FieldRepr, ClassRepr>> findOverridenFields(final FieldRepr f, final ClassRepr c) {
      final Set<Pair<FieldRepr, ClassRepr>> result = new HashSet<Pair<FieldRepr, ClassRepr>>();

      new Object() {
        public void run(final ClassRepr c) {
          final int[] supers = c.getSupers();

          for (int succName : supers) {
            final ClassRepr r = reprByName(succName);

            if (r != null) {
              boolean cont = true;

              if (r.getFields().contains(f)) {
                final FieldRepr ff = r.findField(f.myName);

                if (ff != null) {
                  if (isVisibleIn(r, ff, c)) {
                    result.add(new Pair<FieldRepr, ClassRepr>(ff, r));
                    cont = false;
                  }
                }
              }

              if (cont) {
                run(r);
              }
            }
          }
        }
      }.run(c);

      return result;
    }

    ClassRepr reprByName(final int name) {
      if (myDelta != null) {
        final ClassRepr r = myDelta.getReprByName(name);

        if (r != null) {
          return r;
        }
      }

      return getReprByName(name);
    }

    Option<Boolean> isInheritorOf(final int who, final int whom) {
      if (who == whom) {
        return Option.TRUE;
      }

      final ClassRepr repr = reprByName(who);

      if (repr != null) {
        for (int s : repr.getSupers()) {
          final Option<Boolean> inheritorOf = isInheritorOf(s, whom);
          if (inheritorOf.isDefined() && inheritorOf.value()) {
            return inheritorOf;
          }
        }
      }

      return Option.UNKNOWN;
    }

    Option<Boolean> isSubtypeOf(final TypeRepr.AbstractType who, final TypeRepr.AbstractType whom) {
      if (who.equals(whom)) {
        return Option.TRUE;
      }

      if (who instanceof TypeRepr.PrimitiveType || whom instanceof TypeRepr.PrimitiveType) {
        return Option.FALSE;
      }

      if (who instanceof TypeRepr.ArrayType) {
        if (whom instanceof TypeRepr.ArrayType) {
          return isSubtypeOf(((TypeRepr.ArrayType)who).myElementType, ((TypeRepr.ArrayType)whom).myElementType);
        }

        final String descr = whom.getDescr(myContext);

        if (descr.equals("Ljava/lang/Cloneable") || descr.equals("Ljava/lang/Object") || descr.equals("Ljava/io/Serializable")) {
          return Option.TRUE;
        }

        return Option.FALSE;
      }

      if (whom instanceof TypeRepr.ClassType) {
        return isInheritorOf(((TypeRepr.ClassType)who).myClassName, ((TypeRepr.ClassType)whom).myClassName);
      }

      return Option.FALSE;
    }

    boolean methodVisible(final int className, final MethodRepr m) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.findMethods(MethodRepr.equalByJavaRules(m)).size() > 0) {
          return true;
        }

        return findOverridenMethods(m, r).size() > 0;
      }

      return false;
    }

    boolean fieldVisible(final int className, final FieldRepr field) {
      final ClassRepr r = reprByName(className);

      if (r != null) {
        if (r.getFields().contains(field)) {
          return true;
        }

        return findOverridenFields(field, r).size() > 0;
      }

      return true;
    }

    void affectSubclasses(final int className,
                          final Collection<File> affectedFiles,
                          final Collection<UsageRepr.Usage> affectedUsages,
                          final TIntHashSet dependants,
                          final boolean usages) {
      debug("Affecting subclasses of class: ", className);

      final int fileName = myClassToSourceFile.get(className);

      if (fileName < 0) {
        debug("No source file detected for class ", className);
        debug("End of affectSubclasses");
        return;
      }

      debug("Source file name: ", fileName);

      if (usages) {
        debug("Class usages affection requested");

        final ClassRepr classRepr = reprByName(className);

        if (classRepr != null) {
          debug("Added class usage for ", classRepr.myName);
          affectedUsages.add(classRepr.createUsage());
        }
      }

      final TIntHashSet depClasses = myClassToClassDependency.get(className);

      if (depClasses != null) {
        addAll(dependants, depClasses);
      }

      affectedFiles.add(new File(myContext.getValue(fileName)));

      final TIntHashSet directSubclasses = myClassToSubclasses.get(className);

      if (directSubclasses != null) {
        directSubclasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int subClass) {
            affectSubclasses(subClass, affectedFiles, affectedUsages, dependants, usages);
            return true;
          }
        });
      }
    }

    void affectFieldUsages(final FieldRepr field,
                           final TIntHashSet classes,
                           final UsageRepr.Usage rootUsage,
                           final Set<UsageRepr.Usage> affectedUsages,
                           final TIntHashSet dependents) {
      affectedUsages.add(rootUsage);

      classes.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int p) {
          final TIntHashSet deps = myClassToClassDependency.get(p);

          if (deps != null) {
            addAll(dependents, deps);
          }

          debug("Affect field usage referenced of class ", p);
          affectedUsages.add(rootUsage instanceof UsageRepr.FieldAssignUsage ? field.createAssignUsage(myContext, p) : field.createUsage(myContext, p));
          return true;
        }
      });
    }

    void affectMethodUsages(final MethodRepr method,
                            final TIntHashSet subclasses,
                            final UsageRepr.Usage rootUsage,
                            final Set<UsageRepr.Usage> affectedUsages,
                            final TIntHashSet dependents) {
      affectedUsages.add(rootUsage);

      if (subclasses != null) {
        subclasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int p) {
            final TIntHashSet deps = myClassToClassDependency.get(p);

            if (deps != null) {
              addAll(dependents, deps);
            }

            debug("Affect method usage referenced of class ", p);

            final UsageRepr.Usage usage =
              rootUsage instanceof UsageRepr.MetaMethodUsage ? method.createMetaUsage(myContext, p) : method.createUsage(myContext, p);
            affectedUsages.add(usage);
            return true;
          }
        });
      }
    }

    void affectAll(final int className, final Collection<File> affectedFiles, @Nullable final DependentFilesFilter filter) {
      final int sourceFile = myClassToSourceFile.get(className);
      if (sourceFile > 0) {
        final TIntHashSet dependants = myClassToClassDependency.get(className);
        if (dependants != null) {
          dependants.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int depClass) {
              final int depFile = myClassToSourceFile.get(depClass);
              if (depFile > 0 && depFile != sourceFile) {
                final File theFile = new File(myContext.getValue(depFile));

                if (filter == null || filter.accept(theFile)) {
                  affectedFiles.add(theFile);
                }
              }
              return true;
            }
          });
        }
      }
    }

    public abstract class UsageConstraint {
      public abstract boolean checkResidence(final int residence);
    }

    public class PackageConstraint extends UsageConstraint {
      public final String packageName;

      public PackageConstraint(final String packageName) {
        this.packageName = packageName;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return !ClassRepr.getPackageName(myContext.getValue(residence)).equals(packageName);
      }
    }

    public class InheritanceConstraint extends PackageConstraint {
      public final int rootClass;

      public InheritanceConstraint(final int rootClass) {
        super(ClassRepr.getPackageName(myContext.getValue(rootClass)));
        this.rootClass = rootClass;
      }

      @Override
      public boolean checkResidence(final int residence) {
        final Option<Boolean> inheritorOf = isInheritorOf(residence, rootClass);
        return !inheritorOf.isDefined() || !inheritorOf.value() || super.checkResidence(residence);
      }
    }

    public class NegationConstraint extends UsageConstraint {
      final UsageConstraint x;

      public NegationConstraint(UsageConstraint x) {
        this.x = x;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return !x.checkResidence(residence);
      }
    }

    public class IntersectionConstraint extends UsageConstraint {
      final UsageConstraint x;
      final UsageConstraint y;

      public IntersectionConstraint(final UsageConstraint x, final UsageConstraint y) {
        this.x = x;
        this.y = y;
      }

      @Override
      public boolean checkResidence(final int residence) {
        return x.checkResidence(residence) && y.checkResidence(residence);
      }
    }
  }

  private static boolean isVisibleIn(final ClassRepr c, final ProtoMember m, final ClassRepr scope) {
    final boolean privacy = ((m.myAccess & Opcodes.ACC_PRIVATE) > 0) && c.myName != scope.myName;
    final boolean packageLocality = Difference.isPackageLocal(m.myAccess) && !c.getPackageName().equals(scope.getPackageName());

    return !privacy && !packageLocality;
  }

  private boolean empty(final int s) {
    return s == myEmptyName;
  }

  private TIntHashSet getAllSubclasses(final int root) {
    final TIntHashSet result = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

    addAllSubclasses(root, result);

    return result;
  }

  private void addAllSubclasses(final int root, final TIntHashSet acc) {
    final TIntHashSet directSubclasses = myClassToSubclasses.get(root);

    acc.add(root);

    if (directSubclasses != null) {
      directSubclasses.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int s) {
          if (!acc.contains(s)) {
            addAllSubclasses(s, acc);
          }
          return true;
        }
      });
    }
  }

  private boolean incrementalDecision(final int owner,
                                      final Proto member,
                                      final Collection<File> affectedFiles,
                                      @Nullable final DependentFilesFilter filter) {
    final boolean isField = member instanceof FieldRepr;
    final Util self = new Util(this);

    // Public branch --- hopeless
    if ((member.myAccess & Opcodes.ACC_PUBLIC) > 0) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    // Protected branch
    if ((member.myAccess & Opcodes.ACC_PROTECTED) > 0) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);

      final TIntHashSet propagated = self.propagateFieldAccess(isField ? member.myName : myEmptyName, owner);

      propagated.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int className) {
          final String fileName = myContext.getValue(myClassToSourceFile.get(className));
          debug("Adding ", fileName);
          affectedFiles.add(new File(fileName));
          return true;
        }
      });
    }

    final String packageName = ClassRepr.getPackageName(myContext.getValue(isField ? owner : member.myName));

    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);

    // Package-local branch
    myClassToSourceFile.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int className, int fileName) {
        if (ClassRepr.getPackageName(myContext.getValue(className)).equals(packageName)) {
          final String f = myContext.getValue(fileName);
          final File file = new File(f);
          if (filter == null || filter.accept(file)) {
            debug("Adding: ", f);
            affectedFiles.add(file);
          }
        }
        return true;
      }
    });

    return true;
  }

  public interface DependentFilesFilter {
    DependentFilesFilter ALL_FILES = new DependentFilesFilter() {
      @Override
      public boolean accept(File file) {
        return true;
      }
    };

    boolean accept(File file);
  }

  private class Differential {
    final int DESPERATE_MASK = Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

    final Mappings myDelta;
    final Collection<File> myFilesToCompile;
    final Collection<File> myCompiledFiles;
    final Collection<File> myAffectedFiles;
    @Nullable
    final DependentFilesFilter myFilter;
    @Nullable final Callbacks.ConstantAffectionResolver myConstantSearch;
    final DelayedWorks myDelayedWorks;

    final Util myUpdated;
    final Util mySelf;
    final Util myOriginal;

    final boolean myEasyMode;

    private class DelayedWorks {
      class Triple {
        final int owner;
        final FieldRepr field;
        @Nullable
        final Future<Callbacks.ConstantAffection> affection;

        private Triple(final int owner, final FieldRepr field, @Nullable final Future<Callbacks.ConstantAffection> affection) {
          this.owner = owner;
          this.field = field;
          this.affection = affection;
        }

        Callbacks.ConstantAffection getAffection() {
          try {
            return affection != null ? affection.get() : Callbacks.ConstantAffection.EMPTY;
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }

      final Collection<Triple> myQueue = new LinkedList<Triple>();

      void addConstantWork(final int ownerClass, final FieldRepr changedField, final boolean isRemoved, boolean accessChanged) {
        final Future<Callbacks.ConstantAffection> future;
        if (myConstantSearch == null) {
          future = null;
        }
        else {
          final String className = myContext.getValue(ownerClass);
          final String fieldName = myContext.getValue(changedField.myName);
          future = myConstantSearch.request(className.replace('/', '.'), fieldName, changedField.myAccess, isRemoved, accessChanged);
        }
        myQueue.add(new Triple(ownerClass, changedField, future));
      }

      boolean doWork(final Collection<File> affectedFiles) {
        if (!myQueue.isEmpty()) {
          debug("Starting delayed works.");

          for (final Triple t : myQueue) {
            final Callbacks.ConstantAffection affection = t.getAffection();

            debug("Class: ", t.owner);
            debug("Field: ", t.field.myName);

            if (!affection.isKnown()) {
              debug("No external dependency information available.");
              debug("Trying to soften non-incremental decision.");
              if (!incrementalDecision(t.owner, t.field, affectedFiles, myFilter)) {
                debug("No luck.");
                debug("End of delayed work, returning false.");
                return false;
              }
            }
            else {
              debug("External dependency information retrieved.");
              affectedFiles.addAll(affection.getAffectedFiles());
            }
          }

          debug("End of delayed work, returning true.");
        }
        return true;
      }
    }

    private class FileClasses {
      final int myFileName;
      final Set<ClassRepr> myFileClasses;

      FileClasses(int fileName, Collection<ClassRepr> fileClasses) {
        this.myFileName = fileName;
        this.myFileClasses = new HashSet<ClassRepr>(fileClasses);
      }
    }

    private class DiffState {
      final public TIntHashSet myDependants = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);

      final public Set<UsageRepr.Usage> myAffectedUsages = new HashSet<UsageRepr.Usage>();
      final public Set<UsageRepr.AnnotationUsage> myAnnotationQuery = new HashSet<UsageRepr.AnnotationUsage>();
      final public Map<UsageRepr.Usage, Util.UsageConstraint> myUsageConstraints = new HashMap<UsageRepr.Usage, Util.UsageConstraint>();

      final Difference.Specifier<ClassRepr> myClassDiff;

      private DiffState(Difference.Specifier<ClassRepr> classDiff) {
        this.myClassDiff = classDiff;
      }
    }

    private Differential(final Mappings delta) {
      this.myDelta = delta;
      this.myFilesToCompile = null;
      this.myCompiledFiles = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      this.myConstantSearch = null;

      myDelayedWorks = null;

      myUpdated = null;
      mySelf = null;
      myOriginal = null;

      myEasyMode = true;

      delta.myIsRebuild = true;
    }

    private Differential(final Mappings delta, final Collection<String> removed, final Collection<File> filesToCompile) {
      delta.myRemovedFiles = removed;

      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = null;
      this.myAffectedFiles = null;
      this.myFilter = null;
      this.myConstantSearch = null;

      myDelayedWorks = null;

      myUpdated = new Util(myDelta);
      mySelf = new Util(Mappings.this);
      myOriginal = new Util();

      myEasyMode = true;
    }

    private Differential(final Mappings delta,
                         final Collection<String> removed,
                         final Collection<File> filesToCompile,
                         final Collection<File> compiledFiles,
                         final Collection<File> affectedFiles,
                         final DependentFilesFilter filter,
                         @Nullable final Callbacks.ConstantAffectionResolver constantSearch) {
      delta.myRemovedFiles = removed;

      this.myDelta = delta;
      this.myFilesToCompile = filesToCompile;
      this.myCompiledFiles = compiledFiles;
      this.myAffectedFiles = affectedFiles;
      this.myFilter = filter;
      this.myConstantSearch = constantSearch;

      myDelayedWorks = new DelayedWorks();

      myUpdated = new Util(myDelta);
      mySelf = new Util(Mappings.this);
      myOriginal = new Util();

      myEasyMode = false;
    }

    private void processDisappearedClasses() {
      myDelta.compensateRemovedContent(myFilesToCompile);

      final Collection<String> removed = myDelta.getRemovedFiles();

      if (removed != null) {
        for (final String file : removed) {
          final Collection<ClassRepr> classes = mySourceFileToClasses.get(myContext.get(file));

          if (classes != null) {
            for (ClassRepr c : classes) {
              debug("Affecting usages of removed class ", c.myName);
              myUpdated.affectAll(c.myName, myAffectedFiles, myFilter);
            }
          }
        }
      }
    }

    private void processAddedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing added methods: ");
      for (final MethodRepr m : diff.methods().added()) {
        debug("Method: ", m.myName);

        if (it.isAnnotation()) {
          debug("Class is annotation, skipping method analysis");
          continue;
        }

        if ((it.myAccess & Opcodes.ACC_INTERFACE) > 0 ||
            (it.myAccess & Opcodes.ACC_ABSTRACT) > 0 ||
            (m.myAccess & Opcodes.ACC_ABSTRACT) > 0) {
          debug("Class is abstract, or is interface, or added method in abstract => affecting all subclasses");
          myUpdated.affectSubclasses(it.myName, myAffectedFiles, state.myAffectedUsages, state.myDependants, false);
        }

        TIntHashSet propagated = null;

        if ((m.myAccess & Opcodes.ACC_PRIVATE) == 0 && m.myName != myInitName) {
          final ClassRepr oldIt = getReprByName(it.myName);

          if (oldIt != null && mySelf.findOverridenMethods(m, oldIt).size() > 0) {

          }
          else {
            if (m.myArgumentTypes.length > 0) {
              propagated = myUpdated.propagateMethodAccess(m.myName, it.myName);
              debug("Conservative case on overriding methods, affecting method usages");
              myUpdated.affectMethodUsages(m, propagated, m.createMetaUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
            }
          }
        }

        if ((m.myAccess & Opcodes.ACC_PRIVATE) == 0) {
          final Collection<Pair<MethodRepr, ClassRepr>> affectedMethods = myUpdated.findAllMethodsBySpecificity(m, it);
          final MethodRepr.Predicate overrides = MethodRepr.equalByJavaRules(m);

          if (propagated == null) {
            propagated = myUpdated.propagateMethodAccess(m.myName, it.myName);
          }

          final Collection<MethodRepr> lessSpecific = it.findMethods(myUpdated.lessSpecific(m));

          for (final MethodRepr mm : lessSpecific) {
            if (!mm.equals(m)) {
              debug("Found less specific method, affecting method usages");
              myUpdated
                .affectMethodUsages(mm, propagated, mm.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
            }
          }

          debug("Processing affected by specificity methods");
          for (final Pair<MethodRepr, ClassRepr> p : affectedMethods) {
            final MethodRepr mm = p.first;
            final ClassRepr cc = p.second;

            if (cc == myMockClass) {
              continue;
            }
            final Option<Boolean> inheritorOf = mySelf.isInheritorOf(cc.myName, it.myName);
            final boolean isInheritor = inheritorOf.isDefined() && inheritorOf.value();

            debug("Method: ", mm.myName);
            debug("Class : ", cc.myName);

            if (overrides.satisfy(mm) && isInheritor) {
              debug("Current method overrides that found");

              final int file = myClassToSourceFile.get(cc.myName);

              if (file > 0) {
                final String f = myContext.getValue(file);
                debug("Affecting file ", f);
                myAffectedFiles.add(new File(f));
              }
            }
            else {
              debug("Current method does not override that found");

              final TIntHashSet yetPropagated = mySelf.propagateMethodAccess(mm.myName, it.myName);

              if (isInheritor) {
                final TIntHashSet deps = myClassToClassDependency.get(cc.myName);

                if (deps != null) {
                  addAll(state.myDependants, deps);
                }

                myUpdated.affectMethodUsages(mm, yetPropagated, mm.createUsage(myContext, cc.myName), state.myAffectedUsages, state.myDependants);
              }

              debug("Affecting method usages for that found");
              myUpdated.affectMethodUsages(mm, yetPropagated, mm.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
            }
          }

          final TIntHashSet subClasses = getAllSubclasses(it.myName);

          if (subClasses != null) {
            subClasses.forEach(new TIntProcedure() {
              @Override
              public boolean execute(int subClass) {
                final ClassRepr r = myUpdated.reprByName(subClass);
                final int sourceFileName = myClassToSourceFile.get(subClass);

                if (r != null && sourceFileName > 0) {
                  final int outerClass = r.getOuterClassName();

                  if (myUpdated.methodVisible(outerClass, m)) {
                    final String f = myContext.getValue(sourceFileName);
                    debug("Affecting file due to local overriding: ", f);
                    myAffectedFiles.add(new File(f));
                  }
                }
                return true;
              }
            });
          }
        }
      }
      debug("End of added methods processing");
    }

    private void processRemovedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing removed methods:");
      for (final MethodRepr m : diff.methods().removed()) {
        debug("Method ", m.myName);

        final Collection<Pair<MethodRepr, ClassRepr>> overridenMethods = myUpdated.findOverridenMethods(m, it);
        final TIntHashSet propagated = myUpdated.propagateMethodAccess(m.myName, it.myName);

        if (overridenMethods.size() == 0) {
          debug("No overridden methods found, affecting method usages");
          myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
        }
        else {
          boolean clear = true;

          loop:
          for (final Pair<MethodRepr, ClassRepr> overriden : overridenMethods) {
            final MethodRepr mm = overriden.first;

            if (mm == myMockMethod || !mm.myType.equals(m.myType) || !empty(mm.mySignature) || !empty(m.mySignature)) {
              clear = false;
              break loop;
            }
          }

          if (!clear) {
            debug("No clearly overridden methods found, affecting method usages");
            myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
          }
        }

        final Collection<Pair<MethodRepr, ClassRepr>> overriding = myUpdated.findOverridingMethods(m, it, false);

        for (final Pair<MethodRepr, ClassRepr> p : overriding) {
          final int fName = myClassToSourceFile.get(p.second.myName);
          debug("Affecting file by overriding: ", fName);
          myAffectedFiles.add(new File(myContext.getValue(fName)));
        }

        if ((m.myAccess & Opcodes.ACC_ABSTRACT) == 0) {
          propagated.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int p) {
              if (p != it.myName) {
                final ClassRepr s = myUpdated.reprByName(p);

                if (s != null) {
                  final Collection<Pair<MethodRepr, ClassRepr>> overridenInS = myUpdated.findOverridenMethods(m, s);

                  overridenInS.addAll(overridenMethods);

                  boolean allAbstract = true;
                  boolean visited = false;

                  for (final Pair<MethodRepr, ClassRepr> pp : overridenInS) {
                    final ClassRepr cc = pp.second;

                    if (cc == myMockClass) {
                      visited = true;
                      continue;
                    }

                    if (cc.myName == it.myName) {
                      continue;
                    }

                    visited = true;
                    allAbstract = ((pp.first.myAccess & Opcodes.ACC_ABSTRACT) > 0) || ((cc.myAccess & Opcodes.ACC_INTERFACE) > 0);

                    if (!allAbstract) {
                      break;
                    }
                  }

                  if (allAbstract && visited) {
                    final int source = myClassToSourceFile.get(p);

                    if (source > 0) {
                      final String f = myContext.getValue(source);
                      debug(
                        "Removed method is not abstract & overrides some abstract method which is not then over-overriden in subclass ",
                        p);
                      debug("Affecting subclass source file ", f);
                      myAffectedFiles.add(new File(f));
                    }
                  }
                }
              }
              return true;
            }
          });
        }
      }
      debug("End of removed methods processing");
    }

    private void processChangedMethods(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing changed methods:");
      for (final Pair<MethodRepr, Difference> mr : diff.methods().changed()) {
        final MethodRepr m = mr.first;
        final MethodRepr.Diff d = (MethodRepr.Diff)mr.second;
        final boolean throwsChanged = (d.exceptions().added().size() > 0) || (d.exceptions().changed().size() > 0);

        debug("Method: ", m.myName);

        if (it.isAnnotation()) {
          if (d.defaultRemoved()) {
            debug("Class is annotation, default value is removed => adding annotation query");
            final TIntHashSet l = new TIntHashSet(DEFAULT_SET_CAPACITY, DEFAULT_SET_LOAD_FACTOR);
            l.add(m.myName);
            final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)UsageRepr
              .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.myName), l, null);
            state.myAnnotationQuery.add(annotationUsage);
          }
        }
        else if (d.base() != Difference.NONE || throwsChanged) {
          final TIntHashSet propagated = myUpdated.propagateMethodAccess(m.myName, it.myName);

          boolean affected = false;
          boolean constrained = false;

          final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

          if (d.packageLocalOn()) {
            debug("Method became package-local, affecting method usages outside the package");
            myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), usages, state.myDependants);

            for (final UsageRepr.Usage usage : usages) {
              state.myUsageConstraints.put(usage, myUpdated.new InheritanceConstraint(it.myName));
            }

            state.myAffectedUsages.addAll(usages);
            affected = true;
            constrained = true;
          }

          if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0 || throwsChanged) {
            if (!affected) {
              debug("Return type, throws list or signature changed --- affecting method usages");
              myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), usages, state.myDependants);
              state.myAffectedUsages.addAll(usages);
            }
          }
          else if ((d.base() & Difference.ACCESS) > 0) {
            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0) {
              if (!affected) {
                debug("Added static or private specifier or removed static specifier --- affecting method usages");
                myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
              }

              if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0) {
                debug("Added static specifier --- affecting subclasses");
                myUpdated.affectSubclasses(it.myName, myAffectedFiles, state.myAffectedUsages, state.myDependants, false);
              }
            }
            else {
              if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_PUBLIC) > 0 ||
                  (d.addedModifiers() & Opcodes.ACC_ABSTRACT) > 0) {
                debug("Added final, public or abstract specifier --- affecting subclasses");
                myUpdated.affectSubclasses(it.myName, myAffectedFiles, state.myAffectedUsages, state.myDependants, false);
              }

              if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0 && !((d.removedModifiers() & Opcodes.ACC_PRIVATE) > 0)) {
                if (!constrained) {
                  debug("Added public or package-local method became protected --- affect method usages with protected constraint");
                  if (!affected) {
                    myUpdated.affectMethodUsages(m, propagated, m.createUsage(myContext, it.myName), usages, state.myDependants);
                    state.myAffectedUsages.addAll(usages);
                  }

                  for (final UsageRepr.Usage usage : usages) {
                    state.myUsageConstraints.put(usage, myUpdated.new InheritanceConstraint(it.myName));
                  }
                }
              }
            }
          }
        }
      }
      debug("End of changed methods processing");
    }

    private boolean processAddedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing added fields");

      for (final FieldRepr f : diff.fields().added()) {
        debug("Field: ", f.myName);

        final boolean fPrivate = (f.myAccess & Opcodes.ACC_PRIVATE) > 0;
        final boolean fProtected = (f.myAccess & Opcodes.ACC_PROTECTED) > 0;
        final boolean fPublic = (f.myAccess & Opcodes.ACC_PUBLIC) > 0;
        final boolean fPLocal = !fPrivate && !fProtected && !fPublic;

        if (!fPrivate) {
          final TIntHashSet subClasses = getAllSubclasses(it.myName);
          subClasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int subClass) {
              final ClassRepr r = myUpdated.reprByName(subClass);
              final int sourceFileName = myClassToSourceFile.get(subClass);

              if (r != null && sourceFileName > 0) {
                if (r.isLocal()) {
                  debug(
                    "Affecting local subclass (introduced field can potentially hide surrounding method parameters/local variables): ",
                    sourceFileName);
                  myAffectedFiles.add(new File(myContext.getValue(sourceFileName)));
                }
                else {
                  final int outerClass = r.getOuterClassName();

                  if (!empty(outerClass) && myUpdated.fieldVisible(outerClass, f)) {
                    debug("Affecting inner subclass (introduced field can potentially hide surrounding class fields): ",
                          sourceFileName);
                    myAffectedFiles.add(new File(myContext.getValue(sourceFileName)));
                  }
                }
              }

              debug("Affecting field usages referenced from subclass ", subClass);
              final TIntHashSet propagated = myUpdated.propagateFieldAccess(f.myName, subClass);
              myUpdated.affectFieldUsages(f, propagated, f.createUsage(myContext, subClass), state.myAffectedUsages, state.myDependants);

              final TIntHashSet deps = myClassToClassDependency.get(subClass);

              if (deps != null) {
                addAll(state.myDependants, deps);
              }
              return true;
            }
          });
        }

        final Collection<Pair<FieldRepr, ClassRepr>> overridden = myUpdated.findOverridenFields(f, it);

        for (final Pair<FieldRepr, ClassRepr> p : overridden) {
          final FieldRepr ff = p.first;
          final ClassRepr cc = p.second;

          final boolean ffPrivate = (ff.myAccess & Opcodes.ACC_PRIVATE) > 0;
          final boolean ffProtected = (ff.myAccess & Opcodes.ACC_PROTECTED) > 0;
          final boolean ffPublic = (ff.myAccess & Opcodes.ACC_PUBLIC) > 0;
          final boolean ffPLocal = Difference.isPackageLocal(ff.myAccess);

          if (!ffPrivate) {
            final TIntHashSet propagated = myOriginal.propagateFieldAccess(ff.myName, cc.myName);
            final Set<UsageRepr.Usage> localUsages = new HashSet<UsageRepr.Usage>();

            debug("Affecting usages of overridden field in class ", cc.myName);
            myUpdated.affectFieldUsages(ff, propagated, ff.createUsage(myContext, cc.myName), localUsages, state.myDependants);

            if (fPrivate || (fPublic && (ffPublic || ffPLocal)) || (fProtected && ffProtected) || (fPLocal && ffPLocal)) {

            }
            else {
              Util.UsageConstraint constaint;

              if ((ffProtected && fPublic) || (fProtected && ffPublic) || (ffPLocal && fProtected)) {
                constaint = myUpdated.new NegationConstraint(myUpdated.new InheritanceConstraint(cc.myName));
              }
              else if (ffPublic && ffPLocal) {
                constaint = myUpdated.new NegationConstraint(myUpdated.new PackageConstraint(cc.getPackageName()));
              }
              else {
                constaint =
                  myUpdated.new IntersectionConstraint(myUpdated.new NegationConstraint(myUpdated.new InheritanceConstraint(cc.myName)),
                                                       myUpdated.new NegationConstraint(
                                                         myUpdated.new PackageConstraint(cc.getPackageName())));
              }

              for (final UsageRepr.Usage usage : localUsages) {
                state.myUsageConstraints.put(usage, constaint);
              }
            }

            state.myAffectedUsages.addAll(localUsages);
          }
        }
      }
      debug("End of added fields processing");

      return true;
    }

    private boolean processRemovedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing removed fields:");

      for (final FieldRepr f : diff.fields().removed()) {
        debug("Field: ", f.myName);

        if ((f.myAccess & Opcodes.ACC_PRIVATE) == 0 && (f.myAccess & DESPERATE_MASK) == DESPERATE_MASK && f.hasValue()) {
          debug("Field had value and was (non-private) final static => a switch to non-incremental mode requested");
          if (myConstantSearch != null) {
            myDelayedWorks.addConstantWork(it.myName, f, true, false);
          }
          else {
            if (!incrementalDecision(it.myName, f, myAffectedFiles, myFilter)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }
        }

        final TIntHashSet propagated = myUpdated.propagateFieldAccess(f.myName, it.myName);
        myUpdated.affectFieldUsages(f, propagated, f.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
      }
      debug("End of removed fields processing");

      return true;
    }

    private boolean processChangedFields(final DiffState state, final ClassRepr.Diff diff, final ClassRepr it) {
      debug("Processing changed fields:");

      for (final Pair<FieldRepr, Difference> f : diff.fields().changed()) {
        final Difference d = f.second;
        final FieldRepr field = f.first;

        debug("Field: ", field.myName);

        if ((field.myAccess & Opcodes.ACC_PRIVATE) == 0 && (field.myAccess & DESPERATE_MASK) == DESPERATE_MASK) {
          final int changedModifiers = d.addedModifiers() | d.removedModifiers();
          final boolean harmful = (changedModifiers & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) > 0;
          final boolean accessChanged = (changedModifiers & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) > 0;
          final boolean valueChanged = (d.base() & Difference.VALUE) > 0 && d.hadValue();

          if (harmful || valueChanged || (accessChanged && !d.weakedAccess())) {
            debug("Inline field changed it's access or value => a switch to non-incremental mode requested");
            if (myConstantSearch != null) {
              myDelayedWorks.addConstantWork(it.myName, field, false, accessChanged);
            }
            else {
              if (!incrementalDecision(it.myName, field, myAffectedFiles, myFilter)) {
                debug("End of Differentiate, returning false");
                return false;
              }
            }
          }
        }

        if (d.base() != Difference.NONE) {
          final TIntHashSet propagated = myUpdated.propagateFieldAccess(field.myName, it.myName);

          if ((d.base() & Difference.TYPE) > 0 || (d.base() & Difference.SIGNATURE) > 0) {
            debug("Type or signature changed --- affecting field usages");
            myUpdated
              .affectFieldUsages(field, propagated, field.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
          }
          else if ((d.base() & Difference.ACCESS) > 0) {
            if ((d.addedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.removedModifiers() & Opcodes.ACC_STATIC) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_PRIVATE) > 0 ||
                (d.addedModifiers() & Opcodes.ACC_VOLATILE) > 0) {
              debug("Added/removed static modifier or added private/volatile modifier --- affecting field usages");
              myUpdated
                .affectFieldUsages(field, propagated, field.createUsage(myContext, it.myName), state.myAffectedUsages, state.myDependants);
            }
            else {
              boolean affected = false;
              final Set<UsageRepr.Usage> usages = new HashSet<UsageRepr.Usage>();

              if ((d.addedModifiers() & Opcodes.ACC_FINAL) > 0) {
                debug("Added final modifier --- affecting field assign usages");
                myUpdated.affectFieldUsages(field, propagated, field.createAssignUsage(myContext, it.myName), usages, state.myDependants);
                state.myAffectedUsages.addAll(usages);
                affected = true;
              }

              if ((d.removedModifiers() & Opcodes.ACC_PUBLIC) > 0) {
                debug("Removed public modifier, affecting field usages with appropriate constraint");
                if (!affected) {
                  myUpdated.affectFieldUsages(field, propagated, field.createUsage(myContext, it.myName), usages, state.myDependants);
                  state.myAffectedUsages.addAll(usages);
                }

                for (final UsageRepr.Usage usage : usages) {
                  if ((d.addedModifiers() & Opcodes.ACC_PROTECTED) > 0) {
                    state.myUsageConstraints.put(usage, myUpdated.new InheritanceConstraint(it.myName));
                  }
                  else {
                    state.myUsageConstraints.put(usage, myUpdated.new PackageConstraint(it.getPackageName()));
                  }
                }
              }
            }
          }
        }
      }
      debug("End of changed fields processing");

      return true;
    }

    private boolean processChangedClasses(final DiffState state) {
      debug("Processing changed classes:");
      for (final Pair<ClassRepr, Difference> changed : state.myClassDiff.changed()) {
        final ClassRepr it = changed.first;
        final ClassRepr.Diff diff = (ClassRepr.Diff)changed.second;

        myDelta.addChangedClass(it.myName);

        debug("Changed: ", it.myName);

        final int addedModifiers = diff.addedModifiers();

        final boolean superClassChanged = (diff.base() & Difference.SUPERCLASS) > 0;
        final boolean interfacesChanged = !diff.interfaces().unchanged();
        final boolean signatureChanged = (diff.base() & Difference.SIGNATURE) > 0;

        if (superClassChanged) {
          myDelta.registerRemovedSuperClass(it.myName, ((TypeRepr.ClassType)it.getSuperClass()).myClassName);

          final ClassRepr newClass = myDelta.getReprByName(it.myName);

          assert (newClass != null);

          myDelta.registerAddedSuperClass(it.myName, ((TypeRepr.ClassType)newClass.getSuperClass()).myClassName);
        }

        if (interfacesChanged) {
          for (final TypeRepr.AbstractType typ : diff.interfaces().removed()) {
            myDelta.registerRemovedSuperClass(it.myName, ((TypeRepr.ClassType)typ).myClassName);
          }

          for (final TypeRepr.AbstractType typ : diff.interfaces().added()) {
            myDelta.registerAddedSuperClass(it.myName, ((TypeRepr.ClassType)typ).myClassName);
          }
        }

        if (myEasyMode) {
          return false;
        }

        mySelf.appendDependents(it, state.myDependants);

        if (superClassChanged || interfacesChanged || signatureChanged) {
          debug("Superclass changed: ", superClassChanged);
          debug("Interfaces changed: ", interfacesChanged);
          debug("Signature changed ", signatureChanged);

          final boolean extendsChanged = superClassChanged && !diff.extendsAdded();
          final boolean interfacesRemoved = interfacesChanged && !diff.interfaces().removed().isEmpty();

          debug("Extends changed: ", extendsChanged);
          debug("Interfaces removed: ", interfacesRemoved);

          myUpdated.affectSubclasses(it.myName, myAffectedFiles, state.myAffectedUsages, state.myDependants,
                                     extendsChanged || interfacesRemoved || signatureChanged);
        }

        if ((diff.addedModifiers() & Opcodes.ACC_INTERFACE) > 0 || (diff.removedModifiers() & Opcodes.ACC_INTERFACE) > 0) {
          debug("Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
          state.myAffectedUsages.add(it.createUsage());
        }

        if (it.isAnnotation() && it.getRetentionPolicy() == RetentionPolicy.SOURCE) {
          debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
          if (!incrementalDecision(it.getOuterClassName(), it, myAffectedFiles, myFilter)) {
            debug("End of Differentiate, returning false");
            return false;
          }
        }

        if ((addedModifiers & Opcodes.ACC_PROTECTED) > 0) {
          debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
          final UsageRepr.Usage usage = it.createUsage();

          state.myAffectedUsages.add(usage);
          state.myUsageConstraints.put(usage, myUpdated.new InheritanceConstraint(it.myName));
        }

        if (diff.packageLocalOn()) {
          debug("Introduction of 'package local' access detected, adding class usage + package constraint to affected usages");
          final UsageRepr.Usage usage = it.createUsage();

          state.myAffectedUsages.add(usage);
          state.myUsageConstraints.put(usage, myUpdated.new PackageConstraint(it.getPackageName()));
        }

        if ((addedModifiers & Opcodes.ACC_FINAL) > 0 || (addedModifiers & Opcodes.ACC_PRIVATE) > 0) {
          debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
          state.myAffectedUsages.add(it.createUsage());
        }

        if ((addedModifiers & Opcodes.ACC_ABSTRACT) > 0 || (addedModifiers & Opcodes.ACC_STATIC) > 0) {
          debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
          state.myAffectedUsages.add(UsageRepr.createClassNewUsage(myContext, it.myName));
        }

        if (it.isAnnotation()) {
          debug("Class is annotation, performing annotation-specific analysis");

          if (diff.retentionChanged()) {
            debug("Retention policy change detected, adding class usage to affected usages");
            state.myAffectedUsages.add(it.createUsage());
          }
          else {
            final Collection<ElemType> removedtargets = diff.targets().removed();

            if (removedtargets.contains(ElemType.LOCAL_VARIABLE)) {
              debug("Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
              if (!incrementalDecision(it.getOuterClassName(), it, myAffectedFiles, myFilter)) {
                debug("End of Differentiate, returning false");
                return false;
              }
            }

            if (!removedtargets.isEmpty()) {
              debug("Removed some annotation targets, adding annotation query");
              final UsageRepr.AnnotationUsage annotationUsage = (UsageRepr.AnnotationUsage)UsageRepr
                .createAnnotationUsage(myContext, TypeRepr.createClassType(myContext, it.myName), null, EnumSet.copyOf(removedtargets));
              state.myAnnotationQuery.add(annotationUsage);
            }

            for (final MethodRepr m : diff.methods().added()) {
              if (!m.hasValue()) {
                debug("Added method with no default value: ", m.myName);
                debug("Adding class usage to affected usages");
                state.myAffectedUsages.add(it.createUsage());
              }
            }
          }

          debug("End of annotation-specific analysis");
        }

        processAddedMethods(state, diff, it);
        processRemovedMethods(state, diff, it);
        processChangedMethods(state, diff, it);

        if (!processAddedFields(state, diff, it)) {
          return false;
        }

        if (!processRemovedFields(state, diff, it)) {
          return false;
        }

        if (!processChangedFields(state, diff, it)) {
          return false;
        }
      }
      debug("End of changed classes processing");

      return true;
    }

    private void processRemovedClases(final DiffState state) {
      debug("Processing removed classes:");
      for (final ClassRepr c : state.myClassDiff.removed()) {
        myDelta.addDeletedClass(c);

        final int fileName = myClassToSourceFile.get(c.myName);

        if (fileName != 0) {
          myDelta.myChangedFiles.add(fileName);
        }

        if (!myEasyMode) {
          mySelf.appendDependents(c, state.myDependants);
          debug("Adding usages of class ", c.myName);
          state.myAffectedUsages.add(c.createUsage());
        }
      }
      debug("End of removed classes processing.");
    }

    private void processAddedClasses(final DiffState state) {
      debug("Processing added classes:");
      for (final ClassRepr c : state.myClassDiff.added()) {
        debug("Class name: ", c.myName);
        myDelta.addChangedClass(c.myName);

        for (final int sup : c.getSupers()) {
          myDelta.registerAddedSuperClass(c.myName, sup);
        }

        if (!myEasyMode) {
          final TIntHashSet depClasses = myClassToClassDependency.get(c.myName);

          if (depClasses != null) {
            depClasses.forEach(new TIntProcedure() {
              @Override
              public boolean execute(int depClass) {
                final int fName = myClassToSourceFile.get(depClass);

                if (fName > 0) {
                  final String f = myContext.getValue(fName);
                  final File theFile = new File(f);

                  if (myFilter == null || myFilter.accept(theFile)) {
                    debug("Adding dependent file ", f);
                    myAffectedFiles.add(theFile);
                  }
                }
                return true;
              }
            });
          }
        }
      }

      debug("End of added classes processing.");
    }

    private void calaulateAffectedFiles(final DiffState state) {
      debug("Checking dependent classes:");

      state.myDependants.forEach(new TIntProcedure() {
        @Override
        public boolean execute(final int depClass) {
          final int depFile = myClassToSourceFile.get(depClass);

          if (depFile != 0) {
            final File theFile = new File(myContext.getValue(depFile));

            if (myAffectedFiles.contains(theFile) || myCompiledFiles.contains(theFile)) {
              return true;
            }

            debug("Dependent class: ", depClass);

            final ClassRepr classRepr = getReprByName(depClass);

            if (classRepr == null) {
              return true;
            }

            final Set<UsageRepr.Usage> depUsages = classRepr.getUsages();

            if (depUsages == null) {
              return true;
            }

            for (UsageRepr.Usage usage : depUsages) {
              if (usage instanceof UsageRepr.AnnotationUsage) {
                for (final UsageRepr.AnnotationUsage query : state.myAnnotationQuery) {
                  if (query.satisfies(usage)) {
                    debug("Added file due to annotation query");
                    myAffectedFiles.add(theFile);

                    return true;
                  }
                }
              }
              else if (state.myAffectedUsages.contains(usage)) {
                final Util.UsageConstraint constraint = state.myUsageConstraints.get(usage);

                if (constraint == null) {
                  debug("Added file with no constraints");
                  myAffectedFiles.add(theFile);

                  return true;
                }
                else {
                  if (constraint.checkResidence(depClass)) {
                    debug("Added file with satisfied constraint");
                    myAffectedFiles.add(theFile);

                    return true;
                  }
                }
              }
            }
          }

          return true;
        }
      });
    }

    boolean differentiate() {
      synchronized (myLock) {
        myDelta.myIsDifferentiated = true;

        if (myDelta.myIsRebuild) {
          return true;
        }

        debug("Begin of Differentiate:");
        debug("Easy mode: ", myEasyMode);

        processDisappearedClasses();

        final List<FileClasses> newClasses = new ArrayList<FileClasses>();
        myDelta.mySourceFileToClasses.forEachEntry(new TIntObjectProcedure<Collection<ClassRepr>>() {
          @Override
          public boolean execute(int fileName, Collection<ClassRepr> classes) {
            newClasses.add(new FileClasses(fileName, classes));
            return true;
          }
        });

        for (final FileClasses compiledFile : newClasses) {
          final int fileName = compiledFile.myFileName;
          final Set<ClassRepr> classes = compiledFile.myFileClasses;
          final Set<ClassRepr> pastClasses = (Set<ClassRepr>)mySourceFileToClasses.get(fileName);
          final DiffState state = new DiffState(Difference.make(pastClasses, classes));

          if (!processChangedClasses(state) && !myEasyMode) {
            return false;
          }

          processRemovedClases(state);
          processAddedClasses(state);

          if (!myEasyMode) {
            calaulateAffectedFiles(state);
          }
        }

        debug("End of Differentiate.");

        if (!myEasyMode) {
          final Collection<String> removed = myDelta.getRemovedFiles();

          if (removed != null) {
            for (final String r : removed) {
              myAffectedFiles.remove(new File(r));
            }
          }

          return myDelayedWorks.doWork(myAffectedFiles);
        }
        else {
          return false;
        }
      }
    }
  }

  public void differentiateOnRebuild(final Mappings delta) {
    new Differential(delta).differentiate();
  }

  public void differentiateOnNonIncrementalMake(final Mappings delta,
                                                final Collection<String> removed,
                                                final Collection<File> filesToCompile) {
    new Differential(delta, removed, filesToCompile).differentiate();
  }

  public boolean differentiateOnIncrementalMake
    (final Mappings delta,
     final Collection<String> removed,
     final Collection<File> filesToCompile,
     final Collection<File> compiledFiles,
     final Collection<File> affectedFiles,
     final DependentFilesFilter filter,
     @Nullable final Callbacks.ConstantAffectionResolver constantSearch) {
    return new Differential(delta, removed, filesToCompile, compiledFiles, affectedFiles, filter, constantSearch).differentiate();
  }

  private void cleanupBackDependency(final int className,
                                     @Nullable Set<UsageRepr.Usage> usages,
                                     final IntIntMultiMaplet buffer) {
    if (usages == null) {
      final ClassRepr repr = getReprByName(className);

      if (repr != null) {
        usages = repr.getUsages();
      }
    }

    if (usages != null) {
      for (final UsageRepr.Usage u : usages) {
        buffer.put(u.getOwner(), className);
      }
    }
  }

  private void cleanupRemovedClass(final Mappings delta,
                                   @NotNull final ClassRepr cr,
                                   final Set<UsageRepr.Usage> usages,
                                   final IntIntMultiMaplet dependenciesTrashBin) {
    final int className = cr.myName;

    for (final int superSomething : cr.getSupers()) {
      delta.registerRemovedSuperClass(className, superSomething);
    }

    cleanupBackDependency(className, usages, dependenciesTrashBin);

    myClassToClassDependency.remove(className);
    myClassToSubclasses.remove(className);
    myClassToSourceFile.remove(className);
  }

  public void integrate(final Mappings delta) {
    synchronized (myLock) {
      try {
        assert (delta.isDifferentiated());

        final Collection<String> removed = delta.getRemovedFiles();

        delta.runPostPasses();

        final IntIntMultiMaplet dependenciesTrashBin = new IntIntTransientMultiMaplet();

        if (removed != null) {
          for (final String file : removed) {
            final int fileName = myContext.get(file);
            final Set<ClassRepr> fileClasses = (Set<ClassRepr>)mySourceFileToClasses.get(fileName);

            if (fileClasses != null) {
              for (final ClassRepr aClass : fileClasses) {
                cleanupRemovedClass(delta, aClass, aClass.getUsages(), dependenciesTrashBin);
              }
            }

            mySourceFileToClasses.remove(fileName);
          }
        }

        if (!delta.isRebuild()) {
          for (final ClassRepr repr : delta.getDeletedClasses()) {
            cleanupRemovedClass(delta, repr, repr.getUsages(), dependenciesTrashBin);
          }

          final TIntHashSet superClasses = new TIntHashSet();
          final IntIntTransientMultiMaplet addedSuperClasses = delta.getAddedSuperClasses();
          final IntIntTransientMultiMaplet removedSuperClasses = delta.getRemovedSuperClasses();

          addAllKeys(superClasses, addedSuperClasses);
          addAllKeys(superClasses, removedSuperClasses);

          superClasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int superClass) {
              final TIntHashSet added = addedSuperClasses.get(superClass);
              final TIntHashSet removed = removedSuperClasses.get(superClass);

              final TIntHashSet old = myClassToSubclasses.get(superClass);

              if (old == null) {
                myClassToSubclasses.replace(superClass, added);
              }
              else {
                if (removed != null) {
                  old.removeAll(removed.toArray());
                }

                if (added != null) {
                  old.addAll(added.toArray());
                }

                myClassToSubclasses.replace(superClass, old);
              }

              return true;
            }
          });

          delta.getChangedClasses().forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int className) {
              final int sourceFile = delta.myClassToSourceFile.get(className);
              if (sourceFile > 0) {
                myClassToSourceFile.put(className, sourceFile);
              }
              else {
                myClassToSourceFile.remove(className);
              }

              cleanupBackDependency(className, null, dependenciesTrashBin);

              return true;
            }
          });

          delta.getChangedFiles().forEach(new TIntProcedure() {
            @Override
            public boolean execute(final int fileName) {
              final Collection<ClassRepr> classes = delta.mySourceFileToClasses.get(fileName);

              if (classes != null) {
                mySourceFileToClasses.replace(fileName, classes);
              }
              else {
                mySourceFileToClasses.remove(fileName);
              }

              return true;
            }
          });
        }
        else {
          myClassToSubclasses.putAll(delta.myClassToSubclasses);
          myClassToSourceFile.putAll(delta.myClassToSourceFile);

          mySourceFileToClasses.replaceAll(delta.mySourceFileToClasses);
        }

        // updating classToClass dependencies

        final TIntHashSet affectedClasses = new TIntHashSet();

        addAllKeys(affectedClasses, dependenciesTrashBin);
        addAllKeys(affectedClasses, delta.myClassToClassDependency);

        affectedClasses.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int aClass) {
            final TIntHashSet now = delta.myClassToClassDependency.get(aClass);
            final TIntHashSet toRemove = dependenciesTrashBin.get(aClass);
            final boolean hasDataToAdd = now != null && !now.isEmpty();

            if (toRemove != null && !toRemove.isEmpty()) {
              final TIntHashSet current = myClassToClassDependency.get(aClass);
              if (current != null && !current.isEmpty()) {
                final TIntHashSet before = new TIntHashSet();
                addAll(before, current);

                final boolean removed = current.removeAll(toRemove.toArray());
                final boolean added = hasDataToAdd && current.addAll(now.toArray());

                if ((removed && !added) || (!removed && added) || !before.equals(current)) {
                  myClassToClassDependency.replace(aClass, current);
                }
              }
              else {
                if (hasDataToAdd) {
                  myClassToClassDependency.put(aClass, now);
                }
              }
            }
            else {
              // nothing to remove for this class
              if (hasDataToAdd) {
                myClassToClassDependency.put(aClass, now);
              }
            }
            return true;
          }
        });
      }
      finally {
        delta.close();
      }
    }
  }

  public Callbacks.Backend getCallback() {
    return new Callbacks.Backend() {
      public void associate(final String classFileName, final String sourceFileName, final ClassReader cr) {
        synchronized (myLock) {
          final int classFileNameS = myContext.get(classFileName);
          final Pair<ClassRepr, Set<UsageRepr.Usage>> result = new ClassfileAnalyzer(myContext).analyze(classFileNameS, cr);
          final ClassRepr repr = result.first;
          if (repr != null) {
            final Set<UsageRepr.Usage> localUsages = result.second;
            final int sourceFileNameS = myContext.get(sourceFileName);
            final int className = repr.myName;

            myClassToSourceFile.put(className, sourceFileNameS);
            mySourceFileToClasses.put(sourceFileNameS, repr);

            for (final int s : repr.getSupers()) {
              myClassToSubclasses.put(s, className);
            }

            for (final UsageRepr.Usage u : localUsages) {
              final int owner = u.getOwner();

              if (owner != className) {
                final int ownerSourceFile = myClassToSourceFile.get(owner);

                if (ownerSourceFile > 0) {
                  if (ownerSourceFile != sourceFileNameS) {
                    myClassToClassDependency.put(owner, className);
                  }
                }
                else {
                  myClassToClassDependency.put(owner, className);
                }
              }
            }
          }
        }
      }

      @Override
      public void registerImports(final String className, final Collection<String> imports, Collection<String> staticImports) {
        for (final String s : staticImports) {
          int i = s.length() - 1;
          for (; s.charAt(i) != '.'; i--) ;
          imports.add(s.substring(0, i));
        }

        addPostPass(new PostPass() {
          public void perform() {
            final int rootClassName = myContext.get(className.replace(".", "/"));
            final int fileName = myClassToSourceFile.get(rootClassName);

            for (final String i : imports) {
              if (i.endsWith("*")) {
                continue; // filter out wildcard imports
              }
              final int iname = myContext.get(i.replace(".", "/"));

              myClassToClassDependency.put(iname, rootClassName);

              final ClassRepr repr = getReprByName(rootClassName);

              if (repr != null && fileName != 0) {
                if (repr.addUsage(UsageRepr.createClassUsage(myContext, iname))) {
                  mySourceFileToClasses.put(fileName, repr);
                }
                ;
              }
            }
          }
        });
      }
    };
  }

  @Nullable
  public Set<ClassRepr> getClasses(final String sourceFileName) {
    synchronized (myLock) {
      return (Set<ClassRepr>)mySourceFileToClasses.get(myContext.get(sourceFileName));
    }
  }

  public void close() {
    synchronized (myLock) {
      myClassToSubclasses.close();
      myClassToClassDependency.close();
      mySourceFileToClasses.close();
      myClassToSourceFile.close();

      if (!myIsDelta) {
        // only close if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          context.close();
          myContext = null;
        }
      }
      else {
        FileUtil.delete(myRootDir);
      }
    }
  }

  public void flush(final boolean memoryCachesOnly) {
    synchronized (myLock) {
      myClassToSubclasses.flush(memoryCachesOnly);
      myClassToClassDependency.flush(memoryCachesOnly);
      mySourceFileToClasses.flush(memoryCachesOnly);
      myClassToSourceFile.flush(memoryCachesOnly);

      if (!myIsDelta) {
        // flush if you own the context
        final DependencyContext context = myContext;
        if (context != null) {
          context.clearMemoryCaches();
          if (!memoryCachesOnly) {
            context.flush();
          }
        }
      }
    }
  }

  private static boolean addAll(final TIntHashSet whereToAdd, TIntHashSet whatToAdd) {
    if (whatToAdd.isEmpty()) {
      return false;
    }
    final Ref<Boolean> changed = new Ref<Boolean>(Boolean.FALSE);
    whatToAdd.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int value) {
        if (whereToAdd.add(value)) {
          changed.set(Boolean.TRUE);
        }
        return true;
      }
    });
    return changed.get();
  }

  private static void addAllKeys(final TIntHashSet whereToAdd, final IntIntMultiMaplet maplet) {
    maplet.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int key, TIntHashSet b) {
        whereToAdd.add(key);
        return true;
      }
    });
  }

  private void registerAddedSuperClass(final int aClass, final int superClass) {
    assert (myAddedSuperClasses != null);
    myAddedSuperClasses.put(superClass, aClass);
  }

  private void registerRemovedSuperClass(final int aClass, final int superClass) {
    assert (myRemovedSuperClasses != null);
    myRemovedSuperClasses.put(superClass, aClass);
  }

  private boolean isDifferentiated() {
    return myIsDifferentiated;
  }

  private boolean isRebuild() {
    return myIsRebuild;
  }

  private void addDeletedClass(final ClassRepr cr) {
    assert (myDeletedClasses != null);

    myDeletedClasses.add(cr);

    addChangedClass(cr.myName);
  }

  private void addChangedClass(final int it) {
    assert (myChangedClasses != null && myChangedFiles != null);
    myChangedClasses.add(it);

    final Integer file = myClassToSourceFile.get(it);

    if (file != null) {
      myChangedFiles.add(file);
    }
  }

  @NotNull
  private Set<ClassRepr> getDeletedClasses() {
    return myDeletedClasses == null ? Collections.<ClassRepr>emptySet() : Collections.unmodifiableSet(myDeletedClasses);
  }

  private TIntHashSet getChangedClasses() {
    return myChangedClasses;
  }

  private TIntHashSet getChangedFiles() {
    return myChangedFiles;
  }

  private Collection<String> getRemovedFiles() {
    return myRemovedFiles;
  }

  private static void debug(final String s) {
    LOG.debug(s);
  }

  private void debug(final String comment, final int s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final String s) {
    myDebugS.debug(comment, s);
  }

  private void debug(final String comment, final boolean s) {
    myDebugS.debug(comment, s);
  }

  public void toStream(final PrintStream stream) {
    final Streamable[] data = {
      myClassToSubclasses,
      myClassToClassDependency,
      mySourceFileToClasses,
      myClassToSourceFile,
    };

    final String[] info = {
      "ClassToSubclasses",
      "ClassToClassDependency",
      "SourceFileToClasses",
      "ClassToSourceFile",
      "SourceFileToAnnotationUsages",
      "SourceFileToUsages"
    };

    for (int i = 0; i < data.length; i++) {
      stream.print("Begin Of ");
      stream.println(info[i]);

      data[i].toStream(myContext, stream);

      stream.print("End Of ");
      stream.println(info[i]);
    }
  }

  public void toStream(File outputRoot) {
    final Streamable[] data = {
      myClassToSubclasses,
      myClassToClassDependency,
      mySourceFileToClasses,
      myClassToSourceFile,
    };

    final String[] info = {
      "ClassToSubclasses",
      "ClassToClassDependency",
      "SourceFileToClasses",
      "ClassToSourceFile",
    };

    for (int i = 0; i < data.length; i++) {
      final File file = new File(outputRoot, info[i]);
      FileUtil.createIfDoesntExist(file);
      try {
        final PrintStream stream = new PrintStream(file);
        try {
          data[i].toStream(myContext, stream);
        }
        finally {
          stream.close();
        }
      }
      catch (FileNotFoundException e) {
        e.printStackTrace();
      }
    }
  }
}
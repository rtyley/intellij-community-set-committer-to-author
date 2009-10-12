/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings.ImportLayoutTable;
import com.intellij.psi.codeStyle.CodeStyleSettings.ImportLayoutTable.EmptyLineEntry;
import com.intellij.psi.codeStyle.CodeStyleSettings.ImportLayoutTable.Entry;
import com.intellij.psi.codeStyle.CodeStyleSettings.ImportLayoutTable.PackageEntry;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.impl.source.resolve.ResolveClassUtil;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ImportHelper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.ImportHelper");

  private final CodeStyleSettings mySettings;
  @NonNls private static final String JAVA_LANG_PACKAGE = "java.lang";

  public ImportHelper(@NotNull CodeStyleSettings settings){
    mySettings = settings;
  }

  public PsiImportList prepareOptimizeImportsResult(@NotNull final PsiJavaFile file) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(file.getProject());

    final Set<String> namesToImportStaticly = new THashSet<String>();
    String[] names = collectNamesToImport(file, namesToImportStaticly); // Note: this array may contain "<packageOrClassName>.*" for unresolved imports!
    Arrays.sort(names);

    ArrayList<String> namesList = new ArrayList<String>();
    ImportLayoutTable table = mySettings.IMPORT_LAYOUT_TABLE;
    if (table != null){
      int[] entriesForName = ArrayUtil.newIntArray(names.length);
      for(int i = 0; i < names.length; i++){
        entriesForName[i] = findEntryIndex(names[i]);
      }

      Entry[] entries = table.getEntries();
      for(int i = 0; i < entries.length; i++){
        Entry entry = entries[i];
        if (entry instanceof PackageEntry){
          for(int j = 0; j < names.length; j++){
            if (entriesForName[j] == i){
              namesList.add(names[j]);
              names[j] = null;
            }
          }
        }
      }
    }
    for (String name : names) {
      if (name != null) namesList.add(name);
    }
    names = ArrayUtil.toStringArray(namesList);

    TObjectIntHashMap<String> packageToCountMap = new TObjectIntHashMap<String>();
    TObjectIntHashMap<String> classToCountMap = new TObjectIntHashMap<String>();
    for (String name : names) {
      String packageOrClassName = getPackageOrClassName(name);
      if (packageOrClassName.length() == 0) continue;
      if (namesToImportStaticly.contains(name)) {
        int count = classToCountMap.get(packageOrClassName);
        classToCountMap.put(packageOrClassName, count + 1);
      }
      else {
        int count = packageToCountMap.get(packageOrClassName);
        packageToCountMap.put(packageOrClassName, count + 1);
      }
    }

    final Set<String> classesOrPackagesToImportOnDemand = new THashSet<String>();
    class MyVisitorProcedure implements TObjectIntProcedure<String> {
      private final boolean myIsVisitingPackages;

      MyVisitorProcedure(boolean isVisitingPackages) {
        myIsVisitingPackages = isVisitingPackages;
      }

      public boolean execute(final String packageOrClassName, final int count) {
        if (isToUseImportOnDemand(packageOrClassName, count, !myIsVisitingPackages)){
          classesOrPackagesToImportOnDemand.add(packageOrClassName);
        }
        return true;
      }
    }
    classToCountMap.forEachEntry(new MyVisitorProcedure(false));
    packageToCountMap.forEachEntry(new MyVisitorProcedure(true));

    Set<String> classesToUseSingle = findSingleImports(file, names, classesOrPackagesToImportOnDemand, namesToImportStaticly);

    try {
      final String text = buildImportListText(names, classesOrPackagesToImportOnDemand, classesToUseSingle, namesToImportStaticly);
      String ext = StdFileTypes.JAVA.getDefaultExtension();
      final PsiJavaFile dummyFile = (PsiJavaFile)PsiFileFactory.getInstance(file.getProject())
        .createFileFromText("_Dummy_." + ext, StdFileTypes.JAVA, text);
      codeStyleManager.reformat(dummyFile);

      PsiImportList resultList = dummyFile.getImportList();
      PsiImportList oldList = file.getImportList();
      if (oldList.isReplaceEquivalent(resultList)) return null;
      return resultList;
    }
    catch(IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  private static Set<String> findSingleImports(@NotNull final PsiJavaFile file,
                                               @NotNull String[] names,
                                               @NotNull final Set<String> onDemandImports,
                                               @NotNull Set<String> namesToImportStaticly) {
    final GlobalSearchScope resolveScope = file.getResolveScope();
    Set<String> namesToUseSingle = new THashSet<String>();
    final String thisPackageName = file.getPackageName();
    final Set<String> implicitlyImportedPackages = new THashSet<String>(Arrays.asList(file.getImplicitlyImportedPackages()));
    final PsiManager manager = file.getManager();
    for (String name : names) {
      String prefix = getPackageOrClassName(name);
      if (prefix.length() == 0) continue;
      final boolean isImplicitlyImported = implicitlyImportedPackages.contains(prefix);
      if (!onDemandImports.contains(prefix) && !isImplicitlyImported) continue;
      String shortName = PsiNameHelper.getShortClassName(name);

      String thisPackageClass = thisPackageName.length() > 0 ? thisPackageName + "." + shortName : shortName;
      if (JavaPsiFacade.getInstance(manager.getProject()).findClass(thisPackageClass, resolveScope) != null) {
        namesToUseSingle.add(name);
        continue;
      }
      if (!isImplicitlyImported) {
        String langPackageClass = JAVA_LANG_PACKAGE + "." + shortName; //TODO : JSP!
        if (JavaPsiFacade.getInstance(manager.getProject()).findClass(langPackageClass, resolveScope) != null) {
          namesToUseSingle.add(name);
          continue;
        }
      }
      for (String onDemandName : onDemandImports) {
        if (prefix.equals(onDemandName)) continue;
        if (namesToImportStaticly.contains(name)) {
          PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(onDemandName, resolveScope);
          if (aClass != null) {
            PsiField field = aClass.findFieldByName(shortName, true);
            if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
              namesToUseSingle.add(name);
            }
            else {
              PsiClass inner = aClass.findInnerClassByName(shortName, true);
              if (inner != null && inner.hasModifierProperty(PsiModifier.STATIC)) {
                namesToUseSingle.add(name);
              }
              else {
                PsiMethod[] methods = aClass.findMethodsByName(shortName, true);
                for (PsiMethod method : methods) {
                  if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    namesToUseSingle.add(name);
                  }
                }
              }
            }
          }
        }
        else {
          PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(onDemandName + "." + shortName, resolveScope);
          if (aClass != null) {
            namesToUseSingle.add(name);
          }
        }
      }
    }
    return namesToUseSingle;
  }

  @NotNull
  private static String buildImportListText(@NotNull String[] names,
                                            @NotNull final Set<String> packagesOrClassesToImportOnDemand,
                                            @NotNull final Set<String> namesToUseSingle,
                                            @NotNull Set<String> namesToImportStaticly) {
    final Set<String> importedPackagesOrClasses = new THashSet<String>();
    @NonNls final StringBuilder buffer = new StringBuilder();
    for (String name : names) {
      String packageOrClassName = getPackageOrClassName(name);
      final boolean implicitlyImported = JAVA_LANG_PACKAGE.equals(packageOrClassName);
      boolean useOnDemand = implicitlyImported || packagesOrClassesToImportOnDemand.contains(packageOrClassName);
      if (useOnDemand && namesToUseSingle.contains(name)) {
        useOnDemand = false;
      }
      if (useOnDemand && (importedPackagesOrClasses.contains(packageOrClassName) || implicitlyImported)) continue;
      buffer.append("import ");
      if (namesToImportStaticly.contains(name)) buffer.append("static ");
      if (useOnDemand) {
        importedPackagesOrClasses.add(packageOrClassName);
        buffer.append(packageOrClassName);
        buffer.append(".*");
      }
      else {
        buffer.append(name);
      }
      buffer.append(";\n");
    }

    return buffer.toString();
  }

  /**
   * Adds import if it is needed.
   * @return false when the FQ-name have to be used in code (e.g. when conflicting imports already exist)
   */
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass){
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
    PsiElementFactory factory = facade.getElementFactory();
    PsiResolveHelper helper = facade.getResolveHelper();

    String className = refClass.getQualifiedName();
    if (className == null) return true;
    String packageName = getPackageOrClassName(className);
    String shortName = PsiNameHelper.getShortClassName(className);

    PsiClass conflictSingleRef = findSingleImportByShortName(file, shortName);
    if (conflictSingleRef != null){
      return className.equals(conflictSingleRef.getQualifiedName());
    }

    PsiClass curRefClass = helper.resolveReferencedClass(shortName, file);
    if (file.getManager().areElementsEquivalent(refClass, curRefClass)) {
      return true;
    }

    boolean useOnDemand = true;
    if (packageName.length() == 0){
      useOnDemand = false;
    }

    PsiElement conflictPackageRef = findImportOnDemand(file, packageName);
    if (conflictPackageRef != null) {
      useOnDemand = false;
    }

    List<PsiElement> classesToReimport = new ArrayList<PsiElement>();

    List<PsiJavaCodeReferenceElement> importRefs = getImportsFromPackage(file, packageName);
    if (useOnDemand){
      if (mySettings.USE_SINGLE_CLASS_IMPORTS &&
          importRefs.size() + 1 < mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND &&
          !mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
        useOnDemand = false;
      }
      // name of class we try to import is the same as of the class defined in this file
      if (curRefClass != null) {
        useOnDemand = true;
      }
      // check conflicts
      if (useOnDemand){
        PsiElement[] onDemandRefs = file.getOnDemandImports(false, true);
        if (onDemandRefs.length > 0){
          PsiPackage aPackage = facade.findPackage(packageName);
          if (aPackage != null){
            PsiDirectory[] dirs = aPackage.getDirectories();
            for (PsiDirectory dir : dirs) {
              PsiFile[] files = dir.getFiles(); // do not iterate classes - too slow when not loaded
              for (PsiFile aFile : files) {
                if (aFile instanceof PsiJavaFile) {
                  String name = aFile.getVirtualFile().getNameWithoutExtension();
                  for (PsiElement ref : onDemandRefs) {
                    String refName = ref instanceof PsiClass ? ((PsiClass)ref).getQualifiedName() : ((PsiPackage)ref).getQualifiedName();
                    String conflictClassName = refName + "." + name;
                    GlobalSearchScope resolveScope = file.getResolveScope();
                    PsiClass conflictClass = facade.findClass(conflictClassName, resolveScope);
                    if (conflictClass != null && helper.isAccessible(conflictClass, file, null)) {
                      String conflictClassName2 = aPackage.getQualifiedName() + "." + name;
                      PsiClass conflictClass2 = facade.findClass(conflictClassName2, resolveScope);
                      if (conflictClass2 != null && helper.isAccessible(conflictClass2, file, null)) {
                        if (ReferencesSearch.search(conflictClass, new LocalSearchScope(file), false).findFirst()  != null) {
                        classesToReimport.add(conflictClass);
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    try{
      PsiImportList importList = file.getImportList();
      PsiImportStatement statement;
      if (useOnDemand) {
        statement = factory.createImportStatementOnDemand(packageName);
      } else {
        statement = factory.createImportStatement(refClass);
      }
      importList.add(statement);
      if (useOnDemand) {
        for (PsiJavaCodeReferenceElement ref : importRefs) {
          LOG.assertTrue(ref.getParent() instanceof PsiImportStatement);
          if (!ref.isValid()) continue; // todo[dsl] Q?
          classesToReimport.add(ref.resolve());
          PsiImportStatement importStatement = (PsiImportStatement) ref.getParent();
          importStatement.delete();
        }
      }

      for (PsiElement aClassesToReimport : classesToReimport) {
        PsiClass aClass = (PsiClass)aClassesToReimport;
        if (aClass != null) {
          addImport(file, aClass);
        }
      }
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
    return true;
  }

  @NotNull
  private static List<PsiJavaCodeReferenceElement> getImportsFromPackage(@NotNull PsiJavaFile file, @NotNull String packageName){
    PsiClass[] refs = file.getSingleClassImports(true);
    List<PsiJavaCodeReferenceElement> array = new ArrayList<PsiJavaCodeReferenceElement>();
    for (PsiClass ref1 : refs) {
      String className = ref1.getQualifiedName();
      if (getPackageOrClassName(className).equals(packageName)) {
        final PsiJavaCodeReferenceElement ref = file.findImportReferenceTo(ref1);
        if (ref != null) {
          array.add(ref);
        }
      }
    }
    return array;
  }

  private static PsiClass findSingleImportByShortName(@NotNull PsiJavaFile file, @NotNull String shortClassName){
    PsiClass[] refs = file.getSingleClassImports(true);
    for (PsiClass ref : refs) {
      String className = ref.getQualifiedName();
      if (className != null && PsiNameHelper.getShortClassName(className).equals(shortClassName)) {
        return ref;
      }
    }
    for (PsiClass aClass : file.getClasses()) {
      String className = aClass.getQualifiedName();
      if (className != null && PsiNameHelper.getShortClassName(className).equals(shortClassName)) {
        return aClass;
      }
    }
    return null;
  }

  private static PsiPackage findImportOnDemand(@NotNull PsiJavaFile file, @NotNull String packageName){
    PsiElement[] refs = file.getOnDemandImports(false, true);
    for (PsiElement ref : refs) {
      if (ref instanceof PsiPackage && ((PsiPackage)ref).getQualifiedName().equals(packageName)) {
        return (PsiPackage)ref;
      }
    }
    return null;
  }

  public ASTNode getDefaultAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement){
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return null;

    int entryIndex = findEntryIndex(statement);
    PsiImportStatementBase[] allStatements = list.getAllImportStatements();
    int[] entries = ArrayUtil.newIntArray(allStatements.length);
    List<PsiImportStatementBase> statements = new ArrayList<PsiImportStatementBase>();
    for(int i = 0; i < allStatements.length; i++){
      PsiImportStatementBase statement1 = allStatements[i];
      int entryIndex1 = findEntryIndex(statement1);
      entries[i] = entryIndex1;
      if (entryIndex1 == entryIndex){
        statements.add(statement1);
      }
    }

    if (statements.isEmpty()){
      int index;
      for(index = entries.length - 1; index >= 0; index--){
        if (entries[index] < entryIndex) break;
      }
      index++;
      return index < entries.length ? SourceTreeToPsiMap.psiElementToTree(allStatements[index]) : null;
    }
    else {
      String refText = ref.getCanonicalText();
      if (statement.isOnDemand()){
        refText += ".";
      }

      PsiImportStatementBase insertBefore = null;
      PsiImportStatementBase insertAfter = null;
      for (PsiImportStatementBase statement1 : statements) {
        PsiJavaCodeReferenceElement ref1 = statement1.getImportReference();
        if (ref1 == null) {
          continue;
        }
        String refTextThis = ref1.getCanonicalText();
        if (statement1.isOnDemand()) {
          refTextThis += ".";
        }

        int comp = Comparing.compare(refText, refTextThis);
        if (comp < 0 && insertBefore == null) {
          insertBefore = statement1;
        }
        if (comp > 0) {
          insertAfter = statement1;
        }
      }

      if (insertBefore != null) return insertBefore.getNode();
      if (insertAfter != null) return insertAfter.getNode().getTreeNext();
      return null;
    }
  }

  public int getEmptyLinesBetween(@NotNull PsiImportStatementBase statement1, @NotNull PsiImportStatementBase statement2){
    int index1 = findEntryIndex(statement1);
    int index2 = findEntryIndex(statement2);
    if (index1 == index2) return 0;
    if (index1 > index2) {
      int t = index1;
      index1 = index2;
      index2 = t;
    }
    Entry[] entries = mySettings.IMPORT_LAYOUT_TABLE.getEntries();
    int maxSpace = 0;
    for(int i = index1 + 1; i < index2; i++){
      if (entries[i] instanceof EmptyLineEntry){
        int space = 0;
        do{
          space++;
        } while(entries[++i] instanceof EmptyLineEntry);
        maxSpace = Math.max(maxSpace, space);
      }
    }
    return maxSpace;
  }

  private boolean isToUseImportOnDemand(@NotNull String packageName, int classCount, boolean isStaticImportNeeded){
    if (!mySettings.USE_SINGLE_CLASS_IMPORTS) return true;
    int limitCount = isStaticImportNeeded ? mySettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND :
                     mySettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    if (classCount >= limitCount) return true;
    if (packageName.length() == 0) return false;
    CodeStyleSettings.PackageTable table = mySettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND;
    return table != null && table.contains(packageName);
  }

  private int findEntryIndex(@NotNull String packageName){
    Entry[] entries = mySettings.IMPORT_LAYOUT_TABLE.getEntries();
    PackageEntry bestEntry = null;
    int bestEntryIndex = -1;
    for(int i = 0; i < entries.length; i++){
      Entry entry = entries[i];
      if (entry instanceof PackageEntry){
        PackageEntry packageEntry = (PackageEntry)entry;
        if (packageEntry.matchesPackageName(packageName)){
          if (bestEntry == null){
            bestEntry = packageEntry;
            bestEntryIndex = i;
          }
          else{
            String package1 = bestEntry.getPackageName();
            String package2 = packageEntry.getPackageName();
            if (!bestEntry.isWithSubpackages()) continue;
            if (!packageEntry.isWithSubpackages() || package2.length() > package1.length()) {
              bestEntry = packageEntry;
              bestEntryIndex = i;
            }
          }
        }
      }
    }
    return bestEntryIndex;
  }

  public int findEntryIndex(@NotNull PsiImportStatementBase statement){
    PsiJavaCodeReferenceElement ref = statement.getImportReference();
    if (ref == null) return -1;
    String packageName;
    if (statement.isOnDemand()){
      packageName = ref.getCanonicalText();
    }
    else{
      String className = ref.getCanonicalText();
      packageName = getPackageOrClassName(className);
    }
    return findEntryIndex(packageName);
  }

  @NotNull
  private static String[] collectNamesToImport(@NotNull PsiJavaFile file, @NotNull Set<String> namesToImportStaticly){
    Set<String> names = new THashSet<String>();

    final JspFile jspFile = JspPsiUtil.getJspFile(file);
    collectNamesToImport(names, file, namesToImportStaticly, jspFile);
    if (jspFile != null) {
      PsiFile[] files = ArrayUtil.mergeArrays(JspSpiUtil.getIncludingFiles(jspFile), JspSpiUtil.getIncludedFiles(jspFile), PsiFile.class);
      for (PsiFile includingFile : files) {
        final PsiFile javaRoot = includingFile.getViewProvider().getPsi(StdLanguages.JAVA);
        if (javaRoot instanceof PsiJavaFile && file != javaRoot) {
          collectNamesToImport(names, (PsiJavaFile)javaRoot, namesToImportStaticly, jspFile);
        }
      }
    }

    addUnresolvedImportNames(names, file, namesToImportStaticly);

    return ArrayUtil.toStringArray(names);
  }

  private static void collectNamesToImport(@NotNull final Set<String> names,
                                           @NotNull final PsiJavaFile file,
                                           @NotNull final Set<String> namesToImportStaticly,
                                           PsiFile context) {
    String packageName = file.getPackageName();

    final PsiElement[] roots = file.getPsiRoots();
    for (PsiElement root : roots) {
      addNamesToImport(names, root, packageName, namesToImportStaticly, context);
    }
  }

  private static void addNamesToImport(@NotNull Set<String> names,
                                       @NotNull PsiElement scope,
                                       @NotNull String thisPackageName,
                                       @NotNull Set<String> namesToImportStaticly,
                                       PsiFile context){
    if (scope instanceof PsiImportList) return;

    final LinkedList<PsiElement> stack = new LinkedList<PsiElement>();
    stack.add(scope);
    while (!stack.isEmpty()) {
      final PsiElement child = stack.removeFirst();
      if (child instanceof PsiImportList) continue;
      stack.addAll(Arrays.asList(child.getChildren()));

      for(final PsiReference reference : child.getReferences()){
        if (!(reference instanceof PsiJavaReference)) continue;
        final PsiJavaReference javaReference = (PsiJavaReference)reference;
        if (javaReference instanceof JavaClassReference){
          if(((JavaClassReference)javaReference).getContextReference() != null) continue;
        }
        PsiJavaCodeReferenceElement referenceElement = null;
        if (reference instanceof PsiJavaCodeReferenceElement) {
          referenceElement = (PsiJavaCodeReferenceElement)child;
          if (referenceElement.getQualifier() != null) {
            continue;
          }
          if (reference instanceof PsiJavaCodeReferenceElementImpl
              && ((PsiJavaCodeReferenceElementImpl)reference).getKind() == PsiJavaCodeReferenceElementImpl.CLASS_IN_QUALIFIED_NEW_KIND) {
            continue;
          }
        }

        final JavaResolveResult resolveResult = javaReference.advancedResolve(true);
        PsiElement refElement = resolveResult.getElement();
        if (refElement == null && referenceElement != null) {
          refElement = ResolveClassUtil.resolveClass(referenceElement); // might be uncomplete code
        }

        PsiElement currentFileResolveScope = resolveResult.getCurrentFileResolveScope();
        if (!(currentFileResolveScope instanceof PsiImportStatementBase)) continue;
        if (context != null && currentFileResolveScope instanceof JspxImportStatement && context != ((JspxImportStatement)currentFileResolveScope).getDeclarationFile()) {
          continue;
        }

        if (refElement != null) {
          //Add names imported statically
          if (referenceElement != null) {
            if (currentFileResolveScope instanceof PsiImportStaticStatement) {
              PsiImportStaticStatement importStaticStatement = (PsiImportStaticStatement)currentFileResolveScope;
              String name = importStaticStatement.getImportReference().getCanonicalText();
              if (importStaticStatement.isOnDemand()) {
                String refName = referenceElement.getReferenceName();
                if (refName != null) name = name + "." + refName;
              }
              names.add(name);
              namesToImportStaticly.add(name);
              continue;
            }
          }

          if (refElement instanceof PsiClass) {
            String qName = ((PsiClass)refElement).getQualifiedName();
            if (hasPackage(qName, thisPackageName)) continue;
            names.add(qName);
          }
        }
      }
    }
  }

  private static void addUnresolvedImportNames(@NotNull Set<String> set,
                                               @NotNull PsiJavaFile file,
                                               @NotNull Set<String> namesToImportStaticly) {
    PsiImportStatementBase[] imports = file.getImportList().getAllImportStatements();
    for (PsiImportStatementBase anImport : imports) {
      PsiJavaCodeReferenceElement ref = anImport.getImportReference();
      if (ref == null) continue;
      JavaResolveResult[] results = ref.multiResolve(false);
      if (results.length == 0) {
        String text = ref.getCanonicalText();
        if (anImport.isOnDemand()) {
          text += ".*";
        }
        if (anImport instanceof PsiImportStaticStatement) {
          namesToImportStaticly.add(text);
        }
        set.add(text);
      }
    }
  }

  public static boolean isImplicitlyImported(@NotNull String className, @NotNull PsiJavaFile file) {
    String[] packageNames = file.getImplicitlyImportedPackages();
    for (String packageName : packageNames) {
      if (hasPackage(className, packageName)) return true;
    }
    return false;
  }

  public static boolean hasPackage(@NotNull String className, @NotNull String packageName){
    if (!className.startsWith(packageName)) return false;
    if (className.length() == packageName.length()) return false;
    if (packageName.length() > 0 && className.charAt(packageName.length()) != '.') return false;
    return className.indexOf('.', packageName.length() + 1) < 0;
  }

  @NotNull
  private static String getPackageOrClassName(@NotNull String className){
    int dotIndex = className.lastIndexOf('.');
    return dotIndex < 0 ? "" : className.substring(0, dotIndex);
  }

}

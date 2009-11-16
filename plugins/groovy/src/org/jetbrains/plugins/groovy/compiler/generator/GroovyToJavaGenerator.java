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
package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfiguration;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticMethodImplementation;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.util.containers.CharTrie;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator implements SourceGeneratingCompiler, CompilationStatusListener {
  private static final Map<String, String> typesToInitialValues = new HashMap<String, String>();
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.generator.GroovyToJavaGenerator");

  static {
    typesToInitialValues.put("boolean", "false");
    typesToInitialValues.put("int", "0");
    typesToInitialValues.put("short", "0");
    typesToInitialValues.put("long", "0L");
    typesToInitialValues.put("byte", "0");
    typesToInitialValues.put("char", "'c'");
    typesToInitialValues.put("double", "0D");
    typesToInitialValues.put("float", "0F");
    typesToInitialValues.put("void", "");
  }

  private static final String[] JAVA_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.PROTECTED,
      PsiModifier.PRIVATE,
      PsiModifier.PACKAGE_LOCAL,
      PsiModifier.STATIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL,
      PsiModifier.NATIVE,
      PsiModifier.SYNCHRONIZED,
      PsiModifier.STRICTFP,
      PsiModifier.TRANSIENT,
      PsiModifier.VOLATILE
  };

  private static final String[] JAVA_TYPE_DEFINITION_MODIFIERS = new String[]{
      PsiModifier.PUBLIC,
      PsiModifier.ABSTRACT,
      PsiModifier.FINAL
  };

  private static final CharSequence PREFIX_SEPARATOR = "/";
  private CompileContext myContext;
  private final Project myProject;

  public GroovyToJavaGenerator(Project project) {
    myProject = project;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    myContext = context;

    List<GenerationItem> generationItems = new ArrayList<GenerationItem>();
    GenerationItem item;
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);
    for (VirtualFile file : getGroovyFilesToGenerate(context)) {
      if (compilerManager.isExcludedFromCompilation(file)) continue;
      if (excluded.isExcluded(file)) continue;
      if (compilerConfiguration.isResourceFile(file)) continue;

      final Module module = getModuleByFile(context, file);
      if (module == null || !(module.getModuleType() instanceof JavaModuleType)) {
        continue;
      }

      boolean isInTestSources = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(file);

      final GroovyFileBase psiFile = findPsiFile(file);
      GrTopStatement[] statements = getTopStatementsInReadAction(psiFile);

      boolean needCreateTopLevelClass = !needsCreateClassFromFileName(statements);

      String prefix = "";
      if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
        prefix = getJavaClassPackage((GrPackageDefinition) statements[0]);
      }

      //top level class
      if (needCreateTopLevelClass) {
        generationItems.add(new GenerationItemImpl(prefix + file.getNameWithoutExtension() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()), isInTestSources, file));
      }

      GrTypeDefinition[] typeDefinitions = ApplicationManager.getApplication().runReadAction(new Computable<GrTypeDefinition[]>() {
        public GrTypeDefinition[] compute() {
          return psiFile.getTypeDefinitions();
        }
      });

      for (GrTypeDefinition typeDefinition : typeDefinitions) {
        item = new GenerationItemImpl(prefix + typeDefinition.getName() + "." + "java", module, new TimestampValidityState(file.getTimeStamp()), isInTestSources, file);
        generationItems.add(item);
      }
    }
    return generationItems.toArray(new GenerationItem[generationItems.size()]);
  }

  protected Module getModuleByFile(CompileContext context, VirtualFile file) {
    return context.getModuleByFile(file);
  }

  protected VirtualFile[] getGroovyFilesToGenerate(final CompileContext context) {
    final HashSet<VirtualFile> set = new HashSet<VirtualFile>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        set.addAll(Arrays.asList(context.getProjectCompileScope().getFiles(GroovyFileType.GROOVY_FILE_TYPE, true)));
      }
    });

    return VfsUtil.toVirtualFileArray(set);
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] itemsToGenerate, VirtualFile outputRootDirectory) {
    List<GenerationItem> generatedItems = new ArrayList<GenerationItem>();
    Map<String, GenerationItem> pathsToItemsMap = new HashMap<String, GenerationItem>();

    //puts items witch can be generated
    for (GenerationItem item : itemsToGenerate) {
      pathsToItemsMap.put(item.getPath(), item);
    }

    Set<VirtualFile> vFiles = new HashSet<VirtualFile>();
    for (GenerationItem item : itemsToGenerate) {
      vFiles.add(((GenerationItemImpl) item).getVFile());
    }

    for (VirtualFile vFile : vFiles) {
      //generate java classes form groovy source files
      List<String> generatedJavaFilesRelPaths = generateItems(vFile, outputRootDirectory);
      for (String relPath : generatedJavaFilesRelPaths) {
        GenerationItem generationItem = pathsToItemsMap.get(relPath);
        if (generationItem != null)
          generatedItems.add(generationItem);
      }
    }

    return generatedItems.toArray(new GenerationItem[generatedItems.size()]);
  }

  private GroovyFile findPsiFile(final VirtualFile virtualFile) {
    final GroovyFile[] myFindPsiFile = new GroovyFile[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        myFindPsiFile[0] = (GroovyFile) PsiManager.getInstance(myProject).findFile(virtualFile);
      }
    });

    assert myFindPsiFile[0] != null;
    return myFindPsiFile[0];
  }

  //virtualFile -> PsiFile
  private List<String> generateItems(final VirtualFile item, final VirtualFile outputRootDirectory) {
    ProgressIndicator indicator = getProcessIndicator();
    if (indicator != null) indicator.setText("Generating stubs for " + item.getName() + "...");

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generating stubs for " + item.getName() + "...");
    }

    final GroovyFile file = findPsiFile(item);

    List<String> generatedJavaFilesRelPaths = ApplicationManager.getApplication().runReadAction(new Computable<List<String>>() {
      public List<String> compute() {
        return generate(file, outputRootDirectory);
      }
    });

    assert generatedJavaFilesRelPaths != null;

    return generatedJavaFilesRelPaths;
  }

  protected ProgressIndicator getProcessIndicator() {
    return myContext.getProgressIndicator();
  }

  private List<String> generate(final GroovyFile file, VirtualFile outputRootDirectory) {
    List<String> generatedItemsRelativePaths = new ArrayList<String>();

    GrTopStatement[] statements = getTopStatementsInReadAction(file);

    GrPackageDefinition packageDefinition = null;
    if (statements.length > 0 && statements[0] instanceof GrPackageDefinition) {
      packageDefinition = (GrPackageDefinition) statements[0];
    }

    Set<String> classNames = new THashSet<String>();
    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      classNames.add(typeDefinition.getName());
    }

    if (file.isScript()) {
      VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();
      if (!classNames.contains(StringUtil.capitalize(fileDefinitionName)) &&
          !classNames.contains(StringUtil.decapitalize(fileDefinitionName))) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null) {
          generatedItemsRelativePaths.add(createJavaSourceFile(outputRootDirectory, file, scriptClass, packageDefinition));
        }
      }
    }

    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      generatedItemsRelativePaths.add(createJavaSourceFile(outputRootDirectory, file, typeDefinition, packageDefinition));
    }

    return generatedItemsRelativePaths;
  }

  private static String getJavaClassPackage(GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = packageDefinition.getPackageName();
    prefix = prefix.replace(".", PREFIX_SEPARATOR);
    prefix += PREFIX_SEPARATOR;

    return prefix;
  }

  private String createJavaSourceFile(VirtualFile outputRootDirectory, GroovyFileBase file, @NotNull PsiClass typeDefinition, GrPackageDefinition packageDefinition) {
    //prefix defines structure of directories tree
    String prefix = "";
    if (packageDefinition != null) {
      prefix = getJavaClassPackage(packageDefinition);
    }

    StringBuffer text = new StringBuffer();

    final String typeDefinitionName = typeDefinition.getName();
    writeTypeDefinition(text, typeDefinitionName, typeDefinition, packageDefinition);

    VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
//    String generatedFileRelativePath = prefix + typeDefinitionName + "." + "java";
    String fileShortName = typeDefinitionName + "." + "java";
    createGeneratedFile(text, outputRootDirectory.getPath(), prefix, fileShortName);
    return prefix + typeDefinitionName + "." + "java";
  }

  private static GrTopStatement[] getTopStatementsInReadAction(final GroovyFileBase myPsiFile) {
    if (myPsiFile == null) return new GrTopStatement[0];

    return ApplicationManager.getApplication().runReadAction(new Computable<GrTopStatement[]>() {
      public GrTopStatement[] compute() {
        return myPsiFile.getTopStatements();
      }
    });
  }

  private static boolean needsCreateClassFromFileName(GrTopStatement[] statements) {
    boolean isOnlyInnerTypeDef = true;
    for (GrTopStatement statement : statements) {
      if (!(statement instanceof GrTypeDefinition || statement instanceof GrImportStatement || statement instanceof GrPackageDefinition)) {
        isOnlyInnerTypeDef = false;
        break;
      }
    }
    return isOnlyInnerTypeDef;
  }

  private void writeTypeDefinition(StringBuffer text, String typeDefinitionName, @NotNull PsiClass typeDefinition, GrPackageDefinition packageDefinition) {
    final boolean isScript = typeDefinition instanceof GroovyScriptClass;

    writePackageStatement(text, packageDefinition);

    GrMembersDeclaration[] membersDeclarations = typeDefinition instanceof GrTypeDefinition ? ((GrTypeDefinition) typeDefinition).getMemberDeclarations() : GrMembersDeclaration.EMPTY_ARRAY; //todo

    boolean isClassDef = typeDefinition instanceof GrClassDefinition;
    boolean isInterface = typeDefinition instanceof GrInterfaceDefinition;
    boolean isEnum = typeDefinition instanceof GrEnumTypeDefinition;
    boolean isAtInterface = typeDefinition instanceof GrAnnotationTypeDefinition;


    PsiModifierList modifierList = typeDefinition.getModifierList();

    boolean wasAddedModifiers = modifierList != null && writeTypeDefinitionMethodModifiers(text, modifierList, JAVA_TYPE_DEFINITION_MODIFIERS, typeDefinition.isInterface());
    if (!wasAddedModifiers) {
      text.append("public ");
    }

    if (isInterface) text.append("interface");
    else if (isEnum) text.append("enum");
    else if (isAtInterface) text.append("@interface");
    else text.append("class");

    text.append(" ");

    text.append(typeDefinitionName);

    if (typeDefinition != null) {
      appendTypeParameters(text, typeDefinition);
    }

    text.append(" ");

    if (isScript) {
      text.append("extends ");
      text.append("groovy.lang.Script ");
    } else if (!isEnum && !isAtInterface) {
      final PsiClassType[] extendsClassesTypes = typeDefinition.getExtendsListTypes();

      if (extendsClassesTypes.length > 0) {
        text.append("extends ");
        text.append(computeTypeText(extendsClassesTypes[0], false));
        text.append(" ");
      }
      PsiClassType[] implementsTypes = typeDefinition.getImplementsListTypes();

      if (implementsTypes.length > 0) {
        text.append(isInterface ? "extends " : "implements ");
        int i = 0;
        while (i < implementsTypes.length) {
          if (i > 0) text.append(", ");
          text.append(computeTypeText(implementsTypes[i], false));
          text.append(" ");
          i++;
        }
      }
    }

    text.append("{");

    if (isEnum) {
      writeEnumConstants(text, (GrEnumTypeDefinition) typeDefinition);
    }

    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();


    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    methods.addAll(Arrays.asList(typeDefinition.getMethods()));
    if (isClassDef) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      methods.add(factory.createMethodFromText("public groovy.lang.MetaClass getMetaClass() {}", null));
      methods.add(factory.createMethodFromText("public void setMetaClass(groovy.lang.MetaClass mc) {}", null));
      methods.add(factory.createMethodFromText("public Object invokeMethod(String name, Object args) {}", null));
      methods.add(factory.createMethodFromText("public Object getProperty(String propertyName) {}", null));
      methods.add(factory.createMethodFromText("public void setProperty(String propertyName, Object newValue) {}", null));
    }


    for (PsiMethod method : methods) {
      if (method instanceof GrSyntheticMethodImplementation) {
        continue;
      }

      if (method instanceof GrConstructor) {
        writeConstructor(text, (GrConstructor) method, isEnum);
        continue;
      }

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        PsiParameter[] parametersCopy = new PsiParameter[parameters.length];
        PsiType[] parameterTypes = new PsiType[parameters.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          parametersCopy[i] = parameters[i];
          parameterTypes[i] = parameters[i].getType();
        }

        for (int i = parameters.length - 1; i >= 0; i--) {
          MethodSignature signature = MethodSignatureUtil.createMethodSignature(method.getName(), parameterTypes, method.getTypeParameters(), PsiSubstitutor.EMPTY);
          if (methodSignatures.add(signature)) {
            writeMethod(text, method, parametersCopy);
          }

          PsiParameter parameter = parameters[i];
          if (!(parameter instanceof GrParameter) || !((GrParameter) parameter).isOptional()) break;
          parameterTypes = ArrayUtil.remove(parameterTypes, parameterTypes.length - 1);
          parametersCopy = ArrayUtil.remove(parametersCopy, parametersCopy.length - 1);
        }
      } else {
        MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
        if (methodSignatures.add(signature)) {
          writeMethod(text, method, parameters);
        }
      }
    }

    for (GrMembersDeclaration declaration : membersDeclarations) {
      if (declaration instanceof GrVariableDeclaration) {
        writeVariableDeclarations(text, (GrVariableDeclaration) declaration);
      }
    }

    text.append("}");
  }

  private static void appendTypeParameters(StringBuffer text, PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner.hasTypeParameters()) {
      text.append("<");
      PsiTypeParameter[] parameters = typeParameterListOwner.getTypeParameters();
      for (int i = 0; i < parameters.length; i++) {
        if (i > 0) text.append(", ");
        PsiTypeParameter parameter = parameters[i];
        text.append(parameter.getName());
        PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
        if (extendsListTypes.length > 0) {
          text.append(" extends ");
          for (int j = 0; j < extendsListTypes.length; j++) {
            if (j > 0) text.append(" & ");
            text.append(computeTypeText(extendsListTypes[j], false));
          }
        }
      }
      text.append(">");
    }
  }

  private void writeEnumConstants(StringBuffer text, GrEnumTypeDefinition enumDefinition) {
    text.append("\n  ");
    GrEnumConstant[] enumConstants = enumDefinition.getEnumConstants();
    for (int i = 0; i < enumConstants.length; i++) {
      if (i > 0) text.append(", ");
      GrEnumConstant enumConstant = enumConstants[i];
      text.append(enumConstant.getName());
      PsiMethod constructor = enumConstant.resolveConstructor();
      if (constructor != null) {
        text.append("(");
        writeStubConstructorInvocation(text, constructor, PsiSubstitutor.EMPTY);
        text.append(")");
      }

      GrTypeDefinitionBody block = enumConstant.getAnonymousBlock();
      if (block != null) {
        text.append("{\n");
        for (PsiMethod method : block.getMethods()) {
          writeMethod(text, method, method.getParameterList().getParameters());
        }
        text.append("}");
      }
    }
    text.append(";");
  }

  private static void writeStubConstructorInvocation(StringBuffer text, PsiMethod constructor, PsiSubstitutor substitutor) {
    final PsiParameter[] superParams = constructor.getParameterList().getParameters();
    for (int j = 0; j < superParams.length; j++) {
      if (j > 0) text.append(", ");
      String typeText = getTypeText(substitutor.substitute(superParams[j].getType()), false);
      text.append("(").append(typeText).append(")").append(getDefaultValueText(typeText));
    }
  }

  private static void writePackageStatement(StringBuffer text, GrPackageDefinition packageDefinition) {
    if (packageDefinition != null) {
      text.append("package ");
      text.append(packageDefinition.getPackageName());
      text.append(";");
      text.append("\n");
      text.append("\n");
    }
  }

  private void writeConstructor(final StringBuffer text, final GrConstructor constructor, boolean isEnum) {
    text.append("\n");
    text.append("  ");
    if (!isEnum) {
      text.append("public ");
      //writeMethodModifiers(text, constructor.getModifierList(), JAVA_MODIFIERS);
    }

    /************* name **********/
    //append constructor name
    text.append(constructor.getName());

    /************* parameters **********/
    GrParameter[] parameterList = constructor.getParameters();

    text.append("(");
    String paramType;
    GrTypeElement paramTypeElement;

    for (int i = 0; i < parameterList.length; i++) {
      if (i > 0) text.append(", ");

      GrParameter parameter = parameterList[i];
      paramTypeElement = parameter.getTypeElementGroovy();
      paramType = getTypeText(paramTypeElement);

      text.append(paramType);
      text.append(" ");
      text.append(parameter.getName());
    }

    text.append(")");
    text.append(" ");

    /************* body **********/

    final GrConstructorInvocation constructorInvocation = constructor.getChainingConstructorInvocation();
    if (constructorInvocation != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          GroovyResolveResult resolveResult = constructorInvocation.resolveConstructorGenerics();
          PsiSubstitutor substitutor = resolveResult.getSubstitutor();
          PsiMethod chainedConstructor = (PsiMethod) resolveResult.getElement();
          if (chainedConstructor == null) {
            final GroovyResolveResult[] results = constructorInvocation.multiResolveConstructor();
            if (results.length > 0) {
              int i = 0;
              if (results[i].getElement() == constructor && results.length > 1) {
                i = 1;
              }
              chainedConstructor = (PsiMethod) results[i].getElement();
              substitutor = results[i].getSubstitutor();
            }
          }

          if (chainedConstructor != null) {
            final PsiClassType[] throwsTypes = chainedConstructor.getThrowsList().getReferencedTypes();
            if (throwsTypes.length > 0) {
              text.append(" throws ");
              for (int i = 0; i < throwsTypes.length; i++) {
                if (i > 0) text.append(", ");
                text.append(getTypeText(substitutor.substitute(throwsTypes[i]), false));
              }
            }
          }

          text.append("{\n");

          text.append("    ");
          if (constructorInvocation.isSuperCall()) {
            text.append("super");
          } else {
            text.append("this");
          }
          text.append("(");

          if (chainedConstructor != null) {
            writeStubConstructorInvocation(text, chainedConstructor, substitutor);
          }

          text.append(")");
          text.append(";");
        }
      });

    } else {
      text.append("{\n");
    }

    text.append("\n  }");
    text.append("\n");
  }

  private static String getDefaultValueText(String typeCanonicalText) {
    final String result = typesToInitialValues.get(typeCanonicalText);
    if (result == null) return "null";
    return result;
  }

  private static void writeVariableDeclarations(StringBuffer text, GrVariableDeclaration variableDeclaration) {
    final String type = getTypeText(variableDeclaration.getTypeElementGroovy());
    final String initializer = getDefaultValueText(type);

    final GrModifierList modifierList = variableDeclaration.getModifierList();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(variableDeclaration.getProject()).getNameHelper();
    for (final GrVariable variable : variableDeclaration.getVariables()) {
      String name = variable.getName();
      if (!nameHelper.isIdentifier(name)) {
        continue; //does not have a java image
      }

      text.append("\n  ");
      writeFieldModifiers(text, modifierList, JAVA_MODIFIERS, variable);

      //type
      text.append(type).append(" ").append(name).append(" = ").append(initializer).append(";\n");
    }
  }

  private static void writeMethod(StringBuffer text, PsiMethod method, final PsiParameter[] parameters) {
    if (method == null) return;
    String name = method.getName();
    if (!JavaPsiFacade.getInstance(method.getProject()).getNameHelper().isIdentifier(name))
      return; //does not have a java image

    boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

    PsiModifierList modifierList = method.getModifierList();

    text.append("\n");
    text.append("  ");
    writeMethodModifiers(text, modifierList, JAVA_MODIFIERS);
    if (method.hasTypeParameters()) {
      appendTypeParameters(text, method);
      text.append(" ");
    }

    //append return type
    PsiType retType;
    if (method instanceof GrMethod) {
      retType = ((GrMethod) method).getDeclaredReturnType();
      if (retType == null) retType = TypesUtil.getJavaLangObject((GrMethod) method);
    } else retType = method.getReturnType();

    text.append(getTypeText(retType, false));
    text.append(" ");

    //append method name
    text.append(name);

    /************* parameters **********/

    text.append("(");

    //writes myParameters
    int i = 0;
    while (i < parameters.length) {
      PsiParameter parameter = parameters[i];
      if (parameter == null) continue;

      if (i > 0) text.append(", ");  //append ','

      text.append(getTypeText(parameter.getType(), i == parameters.length - 1));
      text.append(" ");
      text.append(parameter.getName());

      i++;
    }
    text.append(")");
    text.append(" ");

    if (!isAbstract) {
      /************* body **********/
      text.append("{\n");
      text.append("    return ");

      text.append(getDefaultValueText(getTypeText(retType, false)));

      text.append(";");

      text.append("\n  }");
    } else {
      text.append(";");
    }
    text.append("\n");
  }

  private static boolean writeMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private static void writeFieldModifiers(StringBuffer text, GrModifierList modifierList, String[] modifiers, GrVariable variable) {
    final boolean isProperty = variable instanceof GrField && !modifierList.hasExplicitVisibilityModifiers() && !(((GrField)variable).getContainingClass().isInterface());
    if (isProperty) {
      text.append("private ");
    }

    for (String modifierType : modifiers) {
      if (isProperty && modifierType.equals(PsiModifier.PUBLIC)) {
        continue;
      }

      if (modifierList.hasModifierProperty(modifierType)) {
        text.append(modifierType);
        text.append(" ");
      }
    }
  }

  private static boolean writeTypeDefinitionMethodModifiers(StringBuffer text, PsiModifierList modifierList, String[] modifiers, boolean isInterface) {
    boolean wasAddedModifiers = false;
    for (String modifierType : modifiers) {
      if (modifierList.hasModifierProperty(modifierType)) {
        if (PsiModifier.ABSTRACT.equals(modifierType) && isInterface) {
          continue;
        }
        text.append(modifierType);
        text.append(" ");
        wasAddedModifiers = true;
      }
    }
    return wasAddedModifiers;
  }

  private static String getTypeText(GrTypeElement typeElement) {
    if (typeElement == null) {
      return "java.lang.Object";
    } else {
      return computeTypeText(typeElement.getType(), false);
    }
  }

  private static String getTypeText(PsiType type, boolean allowVarargs) {
    if (type == null) {
      return "java.lang.Object";
    } else {
      return computeTypeText(type, allowVarargs);
    }
  }

  private static String computeTypeText(PsiType type, boolean allowVarargs) {
    if (type instanceof PsiArrayType) {
      String componentText = computeTypeText(((PsiArrayType) type).getComponentType(), false);
      if (allowVarargs && type instanceof PsiEllipsisType) return componentText + "...";
      return componentText + "[]";
    }

    String canonicalText = type.getCanonicalText();
    return canonicalText != null ? canonicalText : type.getPresentableText();
  }

  private static void createGeneratedFile(StringBuffer text, String outputDir, String prefix, String generatedItemPath) {
    assert prefix != null;

    String prefixWithoutSeparator = prefix;

    if (!"".equals(prefix)) {
      prefixWithoutSeparator = prefix.substring(0, prefix.length() - PREFIX_SEPARATOR.length());
      new File(outputDir, prefixWithoutSeparator).mkdirs();
    }

    File myFile;
    if (!"".equals(prefix))
      myFile = new File(outputDir + File.separator + prefixWithoutSeparator, generatedItemPath);
    else
      myFile = new File(outputDir, generatedItemPath);

    BufferedWriter writer = null;
    try {
      Writer fileWriter = new FileWriter(myFile);
      writer = new BufferedWriter(fileWriter);
      writer.write(text.toString());
    } catch (IOException e) {
      LOG.error(e);
    } finally {
      try {
        assert writer != null;
        writer.close();
      } catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  CharTrie myTrie = new CharTrie();

  public void compilationFinished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
    myTrie.clear();
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  class GenerationItemImpl implements GenerationItem {
    ValidityState myState;
    private final boolean myInTestSources;
    final Module myModule;
    public int myHashCode;
    private final VirtualFile myVFile;

    public GenerationItemImpl(String path, Module module, ValidityState state, boolean isInTestSources, VirtualFile vFile) {
      myVFile = vFile;
      myModule = module;
      myState = state;
      myInTestSources = isInTestSources;
      myHashCode = myTrie.getHashCode(path);
    }

    public String getPath() {
      return myTrie.getString(myHashCode);
    }

    public ValidityState getValidityState() {
      return myState;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return myInTestSources;
    }

    public VirtualFile getVFile() {
      return myVFile;
    }
  }
}

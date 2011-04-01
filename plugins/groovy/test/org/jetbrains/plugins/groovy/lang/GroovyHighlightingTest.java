/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyResultOfAssignmentUsedInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyUncheckedAssignmentOfMemberOfRawTypeInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyAccessibilityInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyLabeledStatementInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyRangeTypeCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyResultOfObjectAllocationIgnoredInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialConditionalInspection;
import org.jetbrains.plugins.groovy.codeInspection.control.GroovyTrivialIfInspection;
import org.jetbrains.plugins.groovy.codeInspection.metrics.GroovyOverlyLongMethodInspection;
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;

/**
 * @author peter
 */
public class GroovyHighlightingTest extends LightCodeInsightFixtureTestCase {
  public static final DefaultLightProjectDescriptor GROOVY_17_PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("GROOVY").getModifiableModel();
      final VirtualFile groovyJar =
        JarFileSystem.getInstance().refreshAndFindFileByPath(TestUtils.getMockGroovy1_8LibraryName()+"!/");
      modifiableModel.addRoot(groovyJar, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  };

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "highlighting/";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GROOVY_17_PROJECT_DESCRIPTOR;
  }

  public void testDuplicateClosurePrivateVariable() throws Throwable {
    doTest();
  }

  public void testClosureRedefiningVariable() throws Throwable {
    doTest();
  }

  private void doTest(LocalInspectionTool... tools) {
    myFixture.enableInspections(tools);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".groovy");
  }

  public void testCircularInheritance() throws Throwable {
    doTest();
  }

  public void testEmptyTupleType() throws Throwable {
    doTest();
  }

  public void testMapDeclaration() throws Throwable {
    doTest();
  }

  public void testShouldntImplementGroovyObjectMethods() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() throws Throwable {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  private void addGroovyObject() throws IOException {
    myFixture.addClass("package groovy.lang;" +
                       "public interface GroovyObject  {\n" +
                       "    java.lang.Object invokeMethod(java.lang.String s, java.lang.Object o);\n" +
                       "    java.lang.Object getProperty(java.lang.String s);\n" +
                       "    void setProperty(java.lang.String s, java.lang.Object o);\n" +
                       "    groovy.lang.MetaClass getMetaClass();\n" +
                       "    void setMetaClass(groovy.lang.MetaClass metaClass);\n" +
                       "}");
  }

  public void testDuplicateFields() throws Throwable {
    doTest();
  }

  public void testNoDuplicationThroughClosureBorder() throws Throwable {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() throws Throwable {
    doTest();
  }

  public void testSuperClassNotExists() throws Exception {
    doTest();
  }
  public void testDontSimplifyString() throws Throwable { doTest(new GroovyTrivialIfInspection(), new GroovyTrivialConditionalInspection()); }

  public void testRawMethodAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawFieldAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccess() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToMap() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testRawArrayStyleAccessToList() throws Throwable { doTest(new GroovyUncheckedAssignmentOfMemberOfRawTypeInspection()); }

  public void testIncompatibleTypesAssignments() throws Throwable { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testAnonymousClassConstructor() throws Throwable {doTest();}
  public void testAnonymousClassAbstractMethod() throws Throwable {doTest();}
  public void testAnonymousClassStaticMethod() throws Throwable {doTest();}
  public void testAnonymousClassShoudImplementMethods() throws Throwable {doTest();}
  public void testAnonymousClassShouldImplementSubstitutedMethod() throws Exception {doTest();}

  public void testDefaultMapConstructorNamedArgs() throws Throwable {doTest();}
  public void testDefaultMapConstructorNamedArgsError() throws Throwable {doTest();}
  public void testDefaultMapConstructorWhenDefConstructorExists() throws Throwable {doTest();}

  public void testSingleAllocationInClosure() throws Throwable {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}
  public void testUnusedAllocationInClosure() throws Throwable {doTest(new GroovyResultOfObjectAllocationIgnoredInspection());}

  public void testUnresolvedLhsAssignment() throws Throwable { doTest(new GroovyUnresolvedAccessInspection()); }

  public void testUnresolvedMethodCallWithTwoDeclarations() throws Throwable{
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testUnresolvedAccess() throws Exception { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testBooleanProperties() throws Exception { doTest(new GroovyUnresolvedAccessInspection()); }
  public void testUntypedAccess() throws Exception { doTest(new GroovyUntypedAccessInspection()); }

  public void testUnassigned1() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned2() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassigned3() throws Exception { doTest(new UnassignedVariableAccessInspection()); }
  public void testUnassignedTryFinally() throws Exception { doTest(new UnassignedVariableAccessInspection()); }

  public void testUnusedVariable() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDefinitionUsedInClosure2() throws Exception { doTest(new UnusedDefInspection()); }
  public void testDuplicateInnerClass() throws Throwable{doTest();}

  public void testThisInStaticContext() throws Throwable {doTest();}
  public void testLocalVariableInStaticContext() throws Exception {doTest();}

  public void testModifiersInPackageAndImportStatements() throws Throwable {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/"+getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, "x/"+getTestName(false)+".groovy");
  }

  public void testBreakOutside() throws Exception {doTest();}
  public void testUndefinedLabel() throws Exception {doTest();}
  public void testUsedLabel() throws Exception {doTest(new GroovyLabeledStatementInspection());}

  public void testNestedMethods() throws Throwable {
    doTest();
  }

  public void testRawOverridedMethod() throws Exception {doTest();}

  public void testFQNJavaClassesUsages() throws Exception {
    doTest();
  }

  public void testGstringAssignableToString() throws Exception {doTest();}
  public void testGstringAssignableToStringInClosureParameter() throws Exception{doTest();}
  public void testEverythingAssignableToString() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testEachOverRange() throws Exception {doTest();}

  public void testMethodCallWithDefaultParameters() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureWithDefaultParameters() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testClosureCallMethodWithInapplicableArguments() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPathCallIsNotApplicable() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testOverlyLongMethodInspection() throws Exception {
    doTest(new GroovyOverlyLongMethodInspection());
  }

  public void testStringAndGStringUpperBound() throws Exception {doTest();}

  public void testWithMethod() throws Exception {doTest();}
  public void testByteArrayArgument() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testForLoopWithNestedEndlessLoop() throws Exception {doTest(new UnassignedVariableAccessInspection());}
  public void testPrefixIncrementCfa() throws Exception {doTest(new UnusedDefInspection());}
  public void testIfIncrementElseReturn() throws Exception {doTest(new UnusedDefInspection()); }

  public void testArrayLikeAccess() throws Exception {doTest();}

  public void testSetInitializing() throws Exception {doTest();}

  public void testEmptyTupleAssignability() throws Exception {doTest();}

  public void testGrDefFieldsArePrivateInJavaCode() throws Exception {
    myFixture.configureByText("X.groovy", "public class X{def x=5}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSuperConstructorInvocation() throws Exception {doTest();}

  public void testDuplicateMapKeys() throws Exception {doTest();}

  public void testIndexPropertyAccess() throws Exception {doTest();}

  public void testPropertyAndFieldDeclaration() throws Exception {doTest();}

  public void testGenericsMethodUsage() throws Exception {doTest();}

  public void testWildcardInExtendsList() throws Exception {doTest();}

  public void testOverrideAnnotation() throws Exception {doTest();}

  public void testClosureCallWithTupleTypeArgument() throws Exception {doTest();}

  public void testMethodDuplicates() throws Exception {doTest();}

  public void testPutValueToEmptyMap() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}
  public void testPutIncorrectValueToMap() throws Exception {doTest(new GroovyAssignabilityCheckInspection());}

  public void testAmbiguousCodeBlock() throws Exception {doTest();}
  public void testAmbiguousCodeBlockInMethodCall() throws Exception {doTest();}
  public void testNotAmbiguousClosableBlock() throws Exception {doTest();}
  public void testDuplicateParameterInClosableBlock() throws Exception {doTest();}

  public void testCyclicInheritance() throws Exception {doTest();}

  public void testNoDefaultConstructor() throws Exception {doTest();}

  public void testTupleTypeAssignments() throws Exception{doTest(new GroovyAssignabilityCheckInspection());}

  public void testInaccessibleConstructorCall() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSignatureIsNotApplicableToList() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testInheritConstructorsAnnotation() throws Exception {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testCollectionAssignments() {doTest(new GroovyAssignabilityCheckInspection()); }
  public void testReturnAssignability() {doTest(new GroovyAssignabilityCheckInspection()); }

  public void testNumberDuplicatesInMaps() throws Exception {doTest();}

  public void testMapNotAcceptedAsStringParameter()  {doTest(new GroovyAssignabilityCheckInspection());}

  public void testBuiltInTypeInstantiation() {doTest();}

  public void testRawTypeInAssignment() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testSOEInFieldDeclarations() {doTest();}

  public void testVeryLongDfaWithComplexGenerics() {
    IdeaTestUtil.assertTiming("", 10000, 1, new Runnable() {
      @Override
      public void run() {
        doTest(new GroovyAssignabilityCheckInspection(), new UnusedDefInspection());
      }
    });
  }

  public void testWrongAnnotation() {doTest();}

  public void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java");
    doTest();
  }

  public void testMapParamWithNoArgs() {doTest(new GroovyAssignabilityCheckInspection());}

  public void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testRangeType() {
    doTest(new GroovyRangeTypeCheckInspection());
  }

  public void testResolveMetaClass() {
    doTest(new GroovyAccessibilityInspection());
  }

  public void testSOFInDelegate() {
    doTest();
  }

  public void testInheritInterfaceInDelegate() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testMethodImplementedByDelegate() {
    doTest();
  }

  public void testVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testMultipleVarNotAssigned() {
    doTest(new UnassignedVariableAccessInspection());
  }

  public void testTestMarkupStubs() {
    doTest();
  }

  public void testResultOfAssignmentUsed() {
    doTest(new GroovyResultOfAssignmentUsedInspection());
  }

  public void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy", "List<? extends String> l = []; l.get(1)");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testThisTypeInStaticContext() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testDuplicatedNamedArgs() {doTest();}

  public void testAnonymousClassArgList() {
    doTest(new GroovyAssignabilityCheckInspection());
  }
}
package com.intellij.codeInsight.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cdr
 * Date: Nov 25, 2002
 */
public class ControlFlowTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/psi/controlFlow";

  private static void doTestFor(final File file) throws Exception {
    String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(file));
    configureFromFileText(file.getName(), contents);
    // extract factory policy class name
    Pattern pattern = Pattern.compile("^// (\\S*).*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue(matcher.matches());
    final String policyClassName = matcher.group(1);
    final ControlFlowPolicy policy;
    if ("LocalsOrMyInstanceFieldsControlFlowPolicy".equals(policyClassName)) {
      policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    }
    else {
      policy = null;
    }

    final int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = getFile().findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class, false);
    assertTrue("Selected element: "+element, element instanceof PsiCodeBlock);

    ControlFlow controlFlow = ControlFlowFactory.getInstance(getProject()).getControlFlow(element, policy);
    String result = controlFlow.toString().trim();

    final String expectedFullPath = StringUtil.trimEnd(file.getPath(),".java") + ".txt";
    VirtualFile expectedFile = LocalFileSystem.getInstance().findFileByPath(expectedFullPath);
    String expected = new String(expectedFile.contentsToByteArray()).trim();
    expected = expected.replaceAll("\r","");
    assertEquals("Text mismatch (in file "+expectedFullPath+"):\n",expected, result);
  }

  private static void doAllTests() throws Exception {
    final String testDirPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + BASE_PATH;
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".java");
      }
    });
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      doTestFor(file);

      System.out.print((i+1)+" ");
    }
  }

  public void test() throws Exception { doAllTests(); }

  public void testMethodWithOnlyDoWhileStatementHasExitPoints() throws Exception {
    configureFromFileText("a.java", "public class Foo {\n" +
                                    "  public void foo() {\n" +
                                    "    boolean f;\n" +
                                    "    do {\n" +
                                    "      f = something();\n" +
                                    "    } while (f);\n" +
                                    "  }\n" +
                                    "}");
    final PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow = ControlFlowFactory.getInstance(getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    IntArrayList exitPoints = new IntArrayList();
    ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize() -1 , exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
    assertEquals(1, exitPoints.size());
  }
}

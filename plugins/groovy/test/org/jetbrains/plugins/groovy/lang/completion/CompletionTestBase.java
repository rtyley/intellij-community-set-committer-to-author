package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.CompositeCompletionData;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * author ven
 */
public abstract class CompletionTestBase extends JavaCodeInsightFixtureTestCase {

  protected void doTest() throws Throwable {
    doTest("");
  }
  protected void doTest(String directory) throws Throwable {
    final List<String> stringList = TestUtils.readInput(getTestDataPath() + "/" + getTestName(true) + ".test");
    if (directory.length()!=0) directory += "/";
    final String fileName = directory + getTestName(true) + "." + getExtension();
    myFixture.addFileToProject(fileName, stringList.get(0));
    myFixture.configureByFile(fileName);

    CompositeCompletionData.restrictCompletion(addReferenceVariants(), addKeywords());

    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = false;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;


    String result = "";
    try {
      myFixture.completeBasic();

      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      if (lookup != null) {
        List<LookupElement> items = lookup.getItems();
        Collections.sort(items, new Comparator<LookupElement>() {
          public int compare(LookupElement o1, LookupElement o2) {
            return o1.getLookupString().compareTo(o2.getLookupString());
          }
        });
        result = "";
        for (LookupElement item : items) {
          result = result + "\n" + item.getLookupString();
        }
        result = result.trim();
        LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
      }

    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
      CodeInsightSettings.getInstance().AUTOCOMPLETE_COMMON_PREFIX = old;
      CompositeCompletionData.restrictCompletion(true, true);
    }
    assertEquals(stringList.get(1), result);
  }

  protected String getExtension() {
    return "groovy";
  }

  protected FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  protected boolean addKeywords() {
    return true;
  }

  protected boolean addReferenceVariants() {
    return true;
  }
}

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.psi.statistics.impl.StatisticsManagerImpl;
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

/**
 * @author mike
 */
public abstract class LightCompletionTestCase extends LightCodeInsightTestCase {
  protected String myPrefix;
  protected LookupElement[] myItems;
  private CompletionType myType = CompletionType.BASIC;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).clearStatistics();
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    super.tearDown();
  }

  protected void configureByFile(String filePath) throws Exception {
    super.configureByFile(filePath);

    complete();
  }

  protected void configureByFileNoComplete(String filePath) throws Exception {
    super.configureByFile(filePath);
  }

  protected void complete() {
    complete(1);
  }

  protected void complete(final int time) {
    new CodeCompletionHandlerBase(myType).invokeCompletion(getProject(), getEditor(), getFile(), time);

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
    myPrefix = lookup == null ? null : lookup.getItems().get(0).getPrefixMatcher().getPrefix();
  }

  public void setType(CompletionType type) {
    myType = type;
  }

  protected void selectItem(LookupElement item) {
    selectItem(item, (char)0);
  }
  
  protected void selectItem(LookupElement item, char completionChar) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup();
    lookup.setCurrentItem(item);
    if (completionChar == 0 || completionChar == '\n' || completionChar == '\t') {
      lookup.finishLookup(completionChar);
    } else {
      type(completionChar);
    }
  }

  protected void testByCount(int finalCount, @NonNls String... values) {
    if (myItems == null) {
      assertEquals(0, finalCount);
      return;
    }
    int index = 0;
    for (final LookupElement myItem : myItems) {
      for (String value : values) {
        if (value == null) {
          assertFalse("Unacceptable value reached: " + myItem.getLookupString(), true);
        }
        if (value.equals(myItem.getLookupString())) {
          index++;
          break;
        }
      }
    }
    assertEquals(finalCount, index);
  }

  protected void assertStringItems(@NonNls String... items) {
    assertEquals(Arrays.asList(myItems).toString(), items.length, myItems.length);
    for (int i = 0; i < myItems.length; i++) {
      LookupElement item = myItems[i];
      assertEquals(items[i], item.getLookupString());
    }
  }

  protected static LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }
}

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

package com.intellij.historyIntegrTests;


import com.intellij.history.Clock;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.vfs.VirtualFile;

public class GettingContentAtDateTest extends IntegrationTestCase {
  private VirtualFile f;

  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    f = myRoot.createChildData(null, "f.txt");
  }

  public void testContentAtDate() throws Exception {
    setContent(f, "1", TIMESTAMP_INCREMENT);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);

    assertContentAt(0, null);
    assertContentAt(TIMESTAMP_INCREMENT, "1");
    assertContentAt(TIMESTAMP_INCREMENT + TIMESTAMP_INCREMENT/ 2, null);
    assertContentAt(TIMESTAMP_INCREMENT * 2, "2");
    assertContentAt(TIMESTAMP_INCREMENT * 3, null);
  }

  public void testContentAtDateForFilteredFilesIsNull() throws Exception {
    VirtualFile f = myRoot.createChildData(null, "f.class");
    setContent(f, "1", 1111);

    assertContentAt(1111, null);
  }

  public void testGettingFirstAvailableContentAfterPurge() throws Exception {
    Clock.setCurrentTimestamp(10);
    setContent(f, "1", TIMESTAMP_INCREMENT);
    Clock.setCurrentTimestamp(20);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);
    Clock.setCurrentTimestamp(30);
    setContent(f, "3", TIMESTAMP_INCREMENT * 3);

    getVcs().getChangeListInTests().purgeObsolete(5);

    assertContentAt(TIMESTAMP_INCREMENT, null);
    assertContentAt(TIMESTAMP_INCREMENT * 2, "2");
    assertContentAt(TIMESTAMP_INCREMENT * 3, "3");
  }

  public void testGettingMostRecentRevisionContent() throws Exception {
    setContent(f, "1", TIMESTAMP_INCREMENT);
    setContent(f, "2", TIMESTAMP_INCREMENT * 2);

    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp < 10000;
      }
    };
    assertContentAt(c, "2");
  }

  private void assertContentAt(long timestamp, String expected) {
    assertContentAt(comparator(timestamp), expected);
  }

  private void assertContentAt(FileRevisionTimestampComparator c, String expected) {
    byte[] actual = LocalHistory.getInstance().getByteContent(f, c);
    assertEquals(expected, actual == null ? null : new String(actual));
  }

  private FileRevisionTimestampComparator comparator(final long timestamp) {
    return new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp == timestamp;
      }
    };
  }
}
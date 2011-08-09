/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;

import java.io.File;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 07.08.2007
*/
public class RncParsingTest extends AbstractParsingTest {
  public RncParsingTest() {
    super("complex");
  }

  public void testMain() throws Throwable {
   doTest(true);
  }

  public void testElements() throws Throwable {
   doTest(true);
  }

  public void testProperties() throws Throwable {
   doTest(true);
  }

  public void testDatatype() throws Throwable {
   doTest(true);
  }

  public void testRelaxNG() throws Throwable {
   doTest(true);
  }

  public void testDocbook() throws Throwable {
    String name = getTestName(false);
    String fullName = myFullDataPath + File.separatorChar + name + "." + myFileExt;
    File file = new File(fullName);
    byte[] bytes = FileUtil.loadFileBytes(file);
    String utf8 = new String(bytes, CharsetToolkit.UTF8);
    int i = utf8.indexOf("for dates and times");
    assertTrue(utf8, i > 0);

    String hex = toHexString(bytes, i - 5, i + 35, utf8);
    System.out.println(hex);

    doTest(true);
  }

  private static String toHexString(byte[] b, int start, int end, String utf8) {
    final String hexChar = "0123456789abcdef";

    StringBuilder hex = new StringBuilder();
    StringBuilder ch = new StringBuilder();

    for (int i = start; i < end; i++) {
      hex.append(hexChar.charAt((b[i] >> 4) & 0x0f));
      hex.append(hexChar.charAt(b[i] & 0x0f));
      hex.append(" ");
      if (i % 5 == 0) hex.append("   ");

      //ch.append(utf8.charAt(i));
      ch.append((char)b[i]);
      ch.append("  ");
      if (i % 5 == 0) ch.append("   ");
    }
    return hex + "\n" + ch;
  }
}
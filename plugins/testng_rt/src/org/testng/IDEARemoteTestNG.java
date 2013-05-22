/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.testng;


import org.testng.collections.Lists;
import org.testng.remote.strprotocol.GenericMessage;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.xml.XmlSuite;

import java.util.List;

public class IDEARemoteTestNG extends TestNG {

  private static void calculateAllSuites(List<XmlSuite> suites, List<XmlSuite> outSuites) {
    for (XmlSuite s : suites) {
      outSuites.add(s);
      calculateAllSuites(s.getChildSuites(), outSuites);
    }
  }

   public void run() {
    try {
      initializeSuitesAndJarFile();

      List<XmlSuite> suites = Lists.newArrayList();
      calculateAllSuites(m_suites, suites);
      if(suites.size() > 0) {

        int testCount= 0;

        for(int i= 0; i < suites.size(); i++) {
          testCount+= (suites.get(i)).getTests().size();
        }

        GenericMessage gm= new GenericMessage(MessageHelper.GENERIC_SUITE_COUNT);
        gm.setSuiteCount(suites.size());
        gm.setTestCount(testCount);
       // msh.sendMessage(gm);

        addListener((ISuiteListener) new IDEATestNGRemoteListener());
        addListener((ITestListener)  new IDEATestNGRemoteListener());
        super.run();
      }
      else {
        System.err.println("Nothing found to run");
      }
    }
    catch(Throwable cause) {
      cause.printStackTrace(System.err);
    }
  }
}

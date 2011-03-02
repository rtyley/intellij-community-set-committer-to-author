/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.updates;


import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jdom.Element;


public class UpdateStrategyTest extends TestCase {

  //could be if somebody used before previous version of IDEA
  public void testWithUndefinedSelection() {
    final TestUpdateSettings settings = new TestUpdateSettings();
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(BuildNumber.fromString("IU-98.520"), new UpdatesInfoLoader() {
      @Override
      public UpdatesInfo loadUpdatesInfo() {
        return UpdatesInfoXppParserTest.InfoReader.read("idea-same.xml");
      }
    }, settings);

    final CheckForUpdateResult result1 = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result1.getState());
    Assert.assertTrue(result1.isReplacedWithAppDef());
    Assert.assertNull(result1.getNewBuildInSelectedChannel());

    settings.setSelectedChannelId(result1.getSelected().getId());

    //second time load
    strategy = new UpdateStrategy(BuildNumber.fromString("IU-98.520"), new UpdatesInfoLoader() {
      @Override
      public UpdatesInfo loadUpdatesInfo() {
        return UpdatesInfoXppParserTest.InfoReader.read("idea-same.xml");
      }
    }, settings);


    final CheckForUpdateResult result2 = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result2.getState());
    Assert.assertFalse(result2.isReplacedWithAppDef());
    Assert.assertNull(result2.getNewBuildInSelectedChannel());
    Assert.assertEquals(settings.getAppDefaultChannelId(), settings.getSelectedChannelId());
  }


  public void testWithUserSelection() {
    //assume user has version 9 eap - and used eap channel - we want to introduce new eap
    final TestUpdateSettings settings = new TestUpdateSettings("maiaEAP", true, null);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(BuildNumber.fromString("IU-95.429"), new UpdatesInfoLoader() {
      @Override
      public UpdatesInfo loadUpdatesInfo() {
        return new UpdatesInfo(loadUpdateInfo("idea-new9eap.xml"));
      }
    }, settings);

    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    Assert.assertFalse(result.isReplacedWithAppDef());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNotNull(update);
    Assert.assertEquals("95.627", update.getNumber().toString());
  }

  private static Element loadUpdateInfo(final String name) {
    Element rootElement;
    try {
      rootElement = JDOMUtil.loadDocument(UpdatesInfoXppParserTest.class, name).getRootElement();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return rootElement;
  }


  public void testNewChannelAppears() {
    // assume user has version 9 eap subscription (default or selected)
    // and new channel appears - eap of version 10 is there
    final TestUpdateSettings settings = new TestUpdateSettings("maiaEAP", true, null);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(BuildNumber.fromString("IU-95.627"), new UpdatesInfoLoader() {
      @Override
      public UpdatesInfo loadUpdatesInfo() {
        return UpdatesInfoXppParserTest.InfoReader.read("idea-newChannel.xml");
      }
    }, settings);


    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNull(update);

    final UpdateChannel newChannel = result.getNewChannelToPropose();
    Assert.assertNotNull(newChannel);
    Assert.assertEquals("IDEA10EAP", newChannel.getId());
    Assert.assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }

  public void testNewChannelAndNewBuildAppear() {
    //assume user has version 9 eap subscription (default or selected)
    //and new channels appears - eap of version 10 is there
    //and new build withing old channel appears also
    //we need to show only one dialog
    final TestUpdateSettings settings = new TestUpdateSettings("maiaEAP", true, null);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(BuildNumber.fromString("IU-95.429"), new UpdatesInfoLoader() {
      @Override
      public UpdatesInfo loadUpdatesInfo() {
        return UpdatesInfoXppParserTest.InfoReader.read("idea-newChannel.xml");
      }
    }, settings);


    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNotNull(update);
    Assert.assertEquals("95.627", update.getNumber().toString());

    final UpdateChannel newChannel = result.getNewChannelToPropose();
    Assert.assertNotNull(newChannel);
    Assert.assertEquals("IDEA10EAP", newChannel.getId());
    Assert.assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }
}

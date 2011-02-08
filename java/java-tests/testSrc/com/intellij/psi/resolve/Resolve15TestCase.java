/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.resolve;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.ResolveTestCase;

/**
 * @author ven
 */
public abstract class Resolve15TestCase extends ResolveTestCase {
  private LanguageLevel myOldLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }
}

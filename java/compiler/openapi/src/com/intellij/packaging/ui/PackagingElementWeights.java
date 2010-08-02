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
package com.intellij.packaging.ui;

/**
 * @author nik
 */
public class PackagingElementWeights {
  public static final int ARTIFACT = 100;
  public static final int DIRECTORY = 50;
  public static final int DIRECTORY_COPY = 40;
  public static final int EXTRACTED_DIRECTORY = 39;
  public static final int LIBRARY = 30;
  public static final int MODULE = 20;
  public static final int FACET = 10;
  public static final int FILE_COPY = 0;

  private PackagingElementWeights() {
  }
}

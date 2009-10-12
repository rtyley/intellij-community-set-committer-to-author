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
package testData.inspection.localVariableNameWithMistakes.data.java.src;

class SPITest3 {
  public void method() {
    String camelCase<TYPO descr="Typo: In word 'Ttest'">Ttest</TYPO> = "she is reading";
    String <TYPO descr="Typo: In word 'ttest'">ttest</TYPO> = "she is reading";
  }
}

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
class C {
  static class E extends Exception { }
  interface I { void i(); }
  static class E1 extends E implements I { public void i() { } }
  static class E2 extends E implements I { public void i() { } }

  void m(boolean f) {
    try {
      if (f)
        throw new E1();
      else
        throw new E2();
    }
    catch (E1 | E2 e) {
      e.<caret>
    }
  }
}
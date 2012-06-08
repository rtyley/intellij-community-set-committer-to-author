/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.*;

class C {
  interface Simplest {
    void m();
  }
  void use(Simplest s) { }

  interface IntParser {
    int parse(String s);
  }

  interface ListProducer<T> {
    List<T> produce();
  }

  void test() {
    Simplest simplest = <weak_warning descr="Lambda expressions type check is not yet implemented">() -> { }</weak_warning>;
    use(<weak_warning descr="Lambda expressions type check is not yet implemented">() -> { }</weak_warning>);

    IntParser intParser = <weak_warning descr="Lambda expressions type check is not yet implemented">(String s) -> Integer.parseInt(s)</weak_warning>;
    ListProducer<String> listProducer = <weak_warning descr="Lambda expressions type check is not yet implemented"><T>() -> new ArrayList<T>()</weak_warning>;
  }
}
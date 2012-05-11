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
import java.io.*;
import java.util.*;

<error descr="Static imports are not supported at this language level">import static java.lang.Math.*;</error>

@interface <error descr="Annotations are not supported at this language level">Anno</error> { }

<error descr="Annotations are not supported at this language level">@Anno</error>
class UnsupportedFeatures {
  void m(<error descr="Variable arity methods are not supported at this language level">String... args</error>) throws Exception {
    <error descr="For-each loops are not supported at this language level">for (String s : args) { System.out.println(s); }</error>

    List<error descr="Generics are not supported at this language level"><String></error> list =
      new ArrayList<error descr="Diamond types are not supported at this language level"><></error>();

    try { Reader r = new FileReader("/dev/null"); }
    catch (<error descr="Multi-catches are not supported at this language level">FileNotFoundException | IOException e</error>) { e.printStackTrace(); }

    try <error descr="Try-with-resources are not supported at this language level">(Reader r = new FileReader("/dev/null"))</error> { }
  }
}

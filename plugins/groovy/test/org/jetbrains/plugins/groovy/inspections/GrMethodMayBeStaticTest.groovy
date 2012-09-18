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
package org.jetbrains.plugins.groovy.inspections

import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.codeInspection.declaration.GrMethodMayBeStaticInspection
/**
 * @author Max Medvedev
 */
public class GrMethodMayBeStaticTest extends LightGroovyTestCase {
  final String basePath = null

  void testSimple() {
    myFixture.configureByText('_.groovy', '''\
class A {
  def <warning descr="Method can be static">foo</warning>() {
    print 2
  }

  def <warning descr="Method can be static">bar</warning>() {
    [1, 2 ].each {3}
  }

  def <warning descr="Method can be static">abc</warning>() {
    new A().bar()
  }

  def cdef() {
    bar()
  }

  def x() {
    Inner ref = null
  }

  def <warning descr="Method can be static">y</warning>() {
    staticMethod()
  }

  def <warning descr="Method can be static">z</warning>() {
    StaticInner i = null
  }

  def q() {
    staticMethod()
    Inner i
  }

  class Inner {}

  static class StaticInner{}

  static staticMethod(){}
}
''')

    myFixture.enableInspections(GrMethodMayBeStaticInspection)
    myFixture.checkHighlighting(true, false, false)
  }
}

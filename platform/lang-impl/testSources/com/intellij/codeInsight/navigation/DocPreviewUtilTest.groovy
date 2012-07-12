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
package com.intellij.codeInsight.navigation;


import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * @author Denis Zhdanov
 * @since 07/10/2012
 */
class DocPreviewUtilTest {

  @Test
  void classDocPreview() {
    
    def header = '''\
[&lt; 1.7 &gt;] java.lang
 public final class java.lang.String extends Object
 implements java.io.Serializable, java.lang.Comparable&lt;java.lang.String&gt;, java.lang.CharSequence\
'''
    
    def fullText = '''\
<html><head>    <style type="text/css">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head><body><small><b>java.lang</b></small><PRE>public final class <b>java.lang.String</b>
extends <a href="psi_element://java.lang.Object"><code>java.lang.Object</code></a>
implements <a href="psi_element://java.io.Serializable"><code>java.io.Serializable</code></a>,&nbsp;<a href="psi_element://java.lang.Comparable"><code>java.lang.Comparable</code></a>&lt;<a href="psi_element://java.lang.String"><code>java.lang.String</code></a>&gt;,&nbsp;<a href="psi_element://java.lang.CharSequence"><code>java.lang.CharSequence</code></a></PRE>
   The <code>String</code> class represents character strings. All
   string literals in Java programs, such as <code>"abc"</code>, are
   implemented as instances of this class.
   <p>
   Strings are constant; their values cannot be changed after they
   are created. String buffers support mutable strings.
   Because String objects are immutable they can be shared. For example:
   <p><blockquote><pre>
       String str = "abc";
   </pre></blockquote><p>
   is equivalent to:
   <p><blockquote><pre>
       char data[] = {'a', 'b', 'c'};
       String str = new String(data);
   </pre></blockquote><p>
   Here are some more examples of how strings can be used:
   <p><blockquote><pre>
       System.out.println("abc");
       String cde = "cde";
       System.out.println("abc" + cde);
       String c = "abc".substring(2,3);
       String d = cde.substring(1, 2);
   </pre></blockquote>
   <p>
   The class <code>String</code> includes methods for examining
   individual characters of the sequence, for comparing strings, for
   searching strings, for extracting substrings, and for creating a
   copy of a string with all characters translated to uppercase or to
   lowercase. Case mapping is based on the Unicode Standard version
   specified by the <a href="psi_element://java.lang.Character"><code>Character</code></a> class.
   <p>
   The Java language provides special support for the string
   concatenation operator (&nbsp;+&nbsp;), and for conversion of
   other objects to strings. String concatenation is implemented
   through the <code>StringBuilder</code>(or <code>StringBuffer</code>)
   class and its <code>append</code> method.
   String conversions are implemented through the method
   <code>toString</code>, defined by <code>Object</code> and
   inherited by all classes in Java. For additional information on
   string concatenation and conversion, see Gosling, Joy, and Steele,
   <i>The Java Language Specification</i>.
  
   <p> Unless otherwise noted, passing a <tt>null</tt> argument to a constructor
   or method in this class will cause a <a href="psi_element://java.lang.NullPointerException"><code>NullPointerException</code></a> to be
   thrown.
  
   <p>A <code>String</code> represents a string in the UTF-16 format
   in which <em>supplementary characters</em> are represented by <em>surrogate
   pairs</em> (see the section <a href="Character.html#unicode">Unicode
   Character Representations</a> in the <code>Character</code> class for
   more information).
   Index values refer to <code>char</code> code units, so a supplementary
   character uses two positions in a <code>String</code>.
   <p>The <code>String</code> class provides methods for dealing with
   Unicode code points (i.e., characters), in addition to those for
   dealing with Unicode code units (i.e., <code>char</code> values).
  
   <DD><DL><DT><b>Since:</b><DD>JDK1.0</DD></DL></DD><DD><DL><DT><b>See Also:</b><DD><a href="psi_element://java.lang.Object#toString()"><code>Object.toString()</code></a>,
<a href="psi_element://java.lang.StringBuffer"><code>StringBuffer</code></a>,
<a href="psi_element://java.lang.StringBuilder"><code>StringBuilder</code></a>,
<a href="psi_element://java.nio.charset.Charset"><code>Charset</code></a></DD></DL></DD></body></html>\
'''
    
    def expected = '''\
[&lt; 1.7 &gt;] java.lang<PRE>public final class java.lang.String<br/>extends <a href="psi_element://java.lang.Object"><code>Object</code></a><br/>implements <a href="psi_element://java.io.Serializable"><code>java.io.Serializable</code></a>, <a href="psi_element://java.lang.Comparable"><code>java.lang.Comparable</code></a>&lt;<a href="psi_element://java.lang.String"><code>java.lang.String<br/></code></a>&gt;, <a href="psi_element://java.lang.CharSequence"><code>java.lang.CharSequence</code></a></PRE><br/>The <code>String</code> class represents character strings. All string literals<br/>in Java programs, such as <code>"abc"</code>, are implemented as instances<br/><a href='psi_element://java.lang.String'>&lt;more&gt;</a>\
'''

    def actual = DocPreviewUtil.buildPreview(header, "java.lang.String", fullText, 2, 60)
    assertEquals(expected, actual)
  }
}

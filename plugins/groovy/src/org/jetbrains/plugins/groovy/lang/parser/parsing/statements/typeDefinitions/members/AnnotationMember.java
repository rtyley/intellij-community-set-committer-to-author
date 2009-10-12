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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */
public class AnnotationMember implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();

    if (TypeDefinition.parse(builder, parser)) {
      typeDeclStartMarker.drop();
      return true;
    } else {
      typeDeclStartMarker.rollbackTo();
    }

    PsiBuilder.Marker declMarker = builder.mark();

    //typized var definition
    if (Declaration.parse(builder, true, true, parser)) {
      declMarker.drop();
      return true;
    } else {
      declMarker.rollbackTo();
      return false;
    }
  }
}

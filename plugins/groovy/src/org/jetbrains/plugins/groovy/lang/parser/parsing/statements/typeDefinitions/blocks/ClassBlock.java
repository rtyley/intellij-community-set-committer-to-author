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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.ClassMember;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public class ClassBlock implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, String className, GroovyParser parser) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    //allow errors
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!ClassMember.parse(builder, className, parser)) {
        builder.getTokenType();
        builder.advanceLexer();
      }
      if (!(Separators.parse(builder) || ParserUtils.lookAhead(builder, mRCURLY))) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    cbMarker.done(CLASS_BODY);
    return true;
  }
}

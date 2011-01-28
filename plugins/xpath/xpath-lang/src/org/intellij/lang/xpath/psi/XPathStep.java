/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface XPathStep extends XPathExpression {
    @Nullable
    XPathAxisSpecifier getAxisSpecifier();

    @Nullable
    XPathNodeTest getNodeTest();

    @NotNull
    XPathPredicate[] getPredicates();

    @Nullable
    XPathExpression getPreviousStep();

    /** only available with XPath 2.0 PSI */
    @Nullable
    XPathExpression getStep();

    boolean isAbsolute();
}

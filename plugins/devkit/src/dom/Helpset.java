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

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:helpset interface.
 * Type helpset documentation
 * <pre>
 *  helpset is a name of file from PLUGIN/help/ folder
 *   Example: <helpset file="myhelp.jar" path="/Help.hs"/>
 *  
 * </pre>
 */
public interface Helpset extends DomElement {

	/**
	 * Returns the value of the file child.
	 * Attribute file
	 * @return the value of the file child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getFile();


	/**
	 * Returns the value of the path child.
	 * Attribute path
	 * @return the value of the path child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getPath();


}

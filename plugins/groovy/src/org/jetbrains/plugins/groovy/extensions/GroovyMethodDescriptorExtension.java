package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodDescriptorExtension extends GroovyMethodDescriptor {

  public static final ExtensionPointName<GroovyMethodDescriptorExtension> EP_NAME =
    new ExtensionPointName<GroovyMethodDescriptorExtension>("org.intellij.groovy.methodDescriptor");

  @Attribute("class")
  public String className;

}

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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.MultiMap;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

@SuppressWarnings({"HardCodedStringLiteral"})
public class ExtensionsAreaImpl implements ExtensionsArea {
  private final LogProvider myLogger;
  private static final String ATTRIBUTE_AREA = "area";

  private static final Map<String,String> ourDefaultEPs = new HashMap<String, String>();

  static {
    ourDefaultEPs.put(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME, EPAvailabilityListenerExtension.class.getName());
  }

  private static final boolean DEBUG_REGISTRATION = false;

  private final AreaPicoContainerImpl myPicoContainer;
  private final Throwable myCreationTrace;
  private final Map<String,ExtensionPointImpl> myExtensionPoints = new ConcurrentHashMap<String, ExtensionPointImpl>();
  private final Map<String,Throwable> myEPTraces = new HashMap<String, Throwable>();
  private final MultiMap<String, ExtensionPointAvailabilityListener> myAvailabilityListeners = new MultiMap<String, ExtensionPointAvailabilityListener>();
  private final List<Runnable> mySuspendedListenerActions = new ArrayList<Runnable>();
  private boolean myAvailabilityNotificationsActive = true;

  private final AreaInstance myAreaInstance;
  private final String myAreaClass;
  private final Map<Element,ExtensionComponentAdapter> myExtensionElement2extension = new HashMap<Element, ExtensionComponentAdapter>();
  private final Map<String,DefaultPicoContainer> myPluginName2picoContainer = new HashMap<String, DefaultPicoContainer>();

  public ExtensionsAreaImpl(String areaClass, AreaInstance areaInstance, PicoContainer parentPicoContainer, @NotNull LogProvider logger) {
    myCreationTrace = DEBUG_REGISTRATION ? new Throwable("Area creation trace") : null;
    myAreaClass = areaClass;
    myAreaInstance = areaInstance;
    myPicoContainer = new AreaPicoContainerImpl(parentPicoContainer, areaInstance);
    myLogger = logger;
    initialize();
  }

  public ExtensionsAreaImpl(MutablePicoContainer picoContainer, LogProvider logger) {
    this(null, null, picoContainer, logger);
  }

  @TestOnly
  public final void notifyAreaReplaced() {
    for (final ExtensionPointImpl point : myExtensionPoints.values()) {
      point.notifyAreaReplaced(this);
    }
  }

  @Override
  public AreaPicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  MutablePicoContainer getMutablePicoContainer() {
    return myPicoContainer;
  }

  @Override
  public String getAreaClass() {
    return myAreaClass;
  }

  @Override
  public void registerExtensionPoint(String pluginName, Element extensionPointElement) {
    registerExtensionPoint(new DefaultPluginDescriptor(PluginId.getId(pluginName)), extensionPointElement);
  }

  @Override
  public void registerExtensionPoint(PluginDescriptor pluginDescriptor, Element extensionPointElement) {
    assert pluginDescriptor.getPluginId() != null;
    final String pluginId = pluginDescriptor.getPluginId().getIdString();
    String epName = extensionPointElement.getAttributeValue("qualifiedName");
    if (epName == null) {
      final String name = extensionPointElement.getAttributeValue("name");
      if (name == null) {
        throw new RuntimeException("'name' attribute not specified for extension point in '" + pluginId + "' plugin");
      }
      epName = pluginId + '.' + name;
    }

    String beanClassName = extensionPointElement.getAttributeValue("beanClass");
    String interfaceClassName = extensionPointElement.getAttributeValue("interface");
    if (beanClassName == null && interfaceClassName == null) {
      throw new RuntimeException("Neither 'beanClass' nor 'interface' attribute is specified for extension point '" + epName + "' in '" + pluginId + "' plugin");
    }
    if (beanClassName != null && interfaceClassName != null) {
      throw new RuntimeException("Both 'beanClass' and 'interface' attributes are specified for extension point '" + epName + "' in '" + pluginId + "' plugin");
    }

    ExtensionPoint.Kind kind;
    String className;
    if (interfaceClassName != null) {
      className = interfaceClassName;
      kind = ExtensionPoint.Kind.INTERFACE;
    }
    else {
      className = beanClassName;
      kind = ExtensionPoint.Kind.BEAN_CLASS;
    }
    registerExtensionPoint(epName, className, pluginDescriptor, kind);
  }

  @Override
  public void registerExtension(final String pluginName, final Element extensionElement) {
    registerExtension(new DefaultPluginDescriptor(PluginId.getId(pluginName)), extensionElement);
  }

  @Override
  public void registerExtension(final PluginDescriptor pluginDescriptor, final Element extensionElement) {
    final PluginId pluginId = pluginDescriptor.getPluginId();

    String epName = extractEPName(extensionElement);

    ExtensionComponentAdapter adapter;
    final PicoContainer container = getPluginContainer(pluginId.getIdString());
    final ExtensionPoint extensionPoint = getExtensionPoint(epName);
    if (extensionPoint.getKind() == ExtensionPoint.Kind.INTERFACE) {
      String implClass = extensionElement.getAttributeValue("implementation");
      if (implClass == null) {
        throw new RuntimeException("'implementation' attribute not specified for '" + epName + "' extension in '" + pluginId.getIdString() + "' plugin");
      }
      adapter = new ExtensionComponentAdapter(implClass, extensionElement, container, pluginDescriptor, shouldDeserializeInstance(extensionElement));
    }
    else {
      adapter = new ExtensionComponentAdapter(extensionPoint.getClassName(), extensionElement, container, pluginDescriptor, true);
    }
    myExtensionElement2extension.put(extensionElement, adapter);
    internalGetPluginContainer().registerComponent(adapter);
    getExtensionPoint(epName).registerExtensionAdapter(adapter);
  }

  private static boolean shouldDeserializeInstance(Element extensionElement) {
    // has content
    if (!extensionElement.getContent().isEmpty()) return true;
    // has custom attributes
    for (Attribute attribute : (List<Attribute>)extensionElement.getAttributes()) {
      final String name = attribute.getName();
      if (!"implementation".equals(name) && !"id".equals(name) && !"order".equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static String extractEPName(final Element extensionElement) {
    String epName = extensionElement.getAttributeValue("point");

    if (epName == null) {
      final Element parentElement = extensionElement.getParentElement();
      final String ns = parentElement != null ? parentElement.getAttributeValue("defaultExtensionNs"):null;

      if (ns != null) {
        epName = ns + '.' + extensionElement.getName();
      } else {
        Namespace namespace = extensionElement.getNamespace();
        epName = namespace.getURI() + '.' + extensionElement.getName();
      }
    }
    return epName;
  }

  @Override
  public PicoContainer getPluginContainer(String pluginName) {
    return internalGetPluginContainer();
  }

  private MutablePicoContainer internalGetPluginContainer() {
    return myPicoContainer;
  }

  private void disposePluginContainer(String pluginName) {
    DefaultPicoContainer pluginContainer = myPluginName2picoContainer.remove(pluginName);
    if (pluginContainer != null) {
      myPicoContainer.removeChildContainer(pluginContainer);
    }
  }

  @Override
  public void unregisterExtensionPoint(String pluginName, Element extensionPointElement) {
    assert pluginName != null;
    String epName = pluginName + '.' + extensionPointElement.getAttributeValue("name");
    unregisterExtensionPoint(epName);
  }

  @Override
  public void unregisterExtension(String pluginName, Element extensionElement) {
    String epName = extractEPName(extensionElement);
    if (!myExtensionElement2extension.containsKey(extensionElement)) {
      XMLOutputter xmlOutputter = new XMLOutputter();
      Format format = Format.getCompactFormat().setIndent("  ").setTextMode(Format.TextMode.NORMALIZE);
      xmlOutputter.setFormat(format);
      StringWriter stringWriter = new StringWriter();
      try {
        xmlOutputter.output(extensionElement, stringWriter);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      myLogger.warn(stringWriter.toString());
      throw new IllegalArgumentException("Trying to unregister extension element that was never registered");
    }
    ExtensionComponentAdapter adapter = myExtensionElement2extension.remove(extensionElement);
    if (adapter == null) return;
    if (getExtensionPoint(epName).unregisterComponentAdapter(adapter)) {
      MutablePicoContainer pluginContainer = internalGetPluginContainer();
      pluginContainer.unregisterComponent(adapter.getComponentKey());
      if (pluginContainer.getComponentAdapters().isEmpty()) {
        disposePluginContainer(pluginName);
      }
    }
  }

  @SuppressWarnings({"unchecked"})
  private void initialize() {
    for (Map.Entry<String, String> entry : ourDefaultEPs.entrySet()) {
      String epName = entry.getKey();
      registerExtensionPoint(epName, entry.getValue());
    }

    getExtensionPoint(EPAvailabilityListenerExtension.EXTENSION_POINT_NAME).addExtensionPointListener(new ExtensionPointListener() {
      @Override
      @SuppressWarnings({"unchecked"})
      public void extensionRemoved(@NotNull Object extension, final PluginDescriptor pluginDescriptor) {
        EPAvailabilityListenerExtension epListenerExtension = (EPAvailabilityListenerExtension) extension;
        Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(epListenerExtension.getExtensionPointName());
        for (Iterator<ExtensionPointAvailabilityListener> iterator = listeners.iterator(); iterator.hasNext();) {
          ExtensionPointAvailabilityListener listener = iterator.next();
          if (listener.getClass().getName().equals(epListenerExtension.getListenerClass())) {
            iterator.remove();
            return;
          }
        }
        myLogger.warn("Failed to find EP availability listener: " + epListenerExtension.getListenerClass());
      }

      @Override
      public void extensionAdded(@NotNull Object extension, final PluginDescriptor pluginDescriptor) {
        EPAvailabilityListenerExtension epListenerExtension = (EPAvailabilityListenerExtension) extension;
        try {
          String epName = epListenerExtension.getExtensionPointName();

          ExtensionPointAvailabilityListener listener = (ExtensionPointAvailabilityListener) instantiate(epListenerExtension.loadListenerClass());
          addAvailabilityListener(epName, listener);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private Object instantiate(Class clazz) {
    ConstructorInjectionComponentAdapter adapter =
      new ConstructorInjectionComponentAdapter(Integer.toString(System.identityHashCode(new Object())), clazz);

    return adapter.getComponentInstance(getPicoContainer());
  }

  @SuppressWarnings({"UnusedDeclaration"})
  public Throwable getCreationTrace() {
    return myCreationTrace;
  }

  @Override
  public void addAvailabilityListener(String epName, ExtensionPointAvailabilityListener listener) {
    myAvailabilityListeners.putValue(epName, listener);
    if (hasExtensionPoint(epName)) {
      notifyAvailableListener(listener, myExtensionPoints.get(epName));
    }
  }

  @Override
  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, ExtensionPoint.Kind.INTERFACE);
  }

  @Override
  public void registerExtensionPoint(@NonNls String extensionPointName, String extensionPointBeanClass, ExtensionPoint.Kind kind) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, new UndefinedPluginDescriptor(), kind);
  }

  @Override
  public void registerExtensionPoint(final String extensionPointName, String extensionPointBeanClass, PluginDescriptor descriptor) {
    registerExtensionPoint(extensionPointName, extensionPointBeanClass, descriptor, ExtensionPoint.Kind.INTERFACE);
  }

  private void registerExtensionPoint(final String extensionPointName,
                                     String extensionPointBeanClass,
                                     PluginDescriptor descriptor,
                                     ExtensionPoint.Kind kind) {
    if (hasExtensionPoint(extensionPointName)) {
      if (DEBUG_REGISTRATION) {
        final ExtensionPointImpl oldEP = getExtensionPoint(extensionPointName);
        myLogger.error("Duplicate registration for EP: " + extensionPointName + ": original plugin " + oldEP.getDescriptor().getPluginId() +
                       ", new plugin " + descriptor.getPluginId(),
                       myEPTraces.get(extensionPointName));
      }
      throw new RuntimeException("Duplicate registration for EP: " + extensionPointName);
    }

    registerExtensionPoint(new ExtensionPointImpl(extensionPointName, extensionPointBeanClass, kind, this, myAreaInstance, myLogger, descriptor));
  }

  public void registerExtensionPoint(final ExtensionPointImpl extensionPoint) {
    final String name = extensionPoint.getName();
    myExtensionPoints.put(name, extensionPoint);
    notifyEPRegistered(extensionPoint);
    if (DEBUG_REGISTRATION) {
      myEPTraces.put(name, new Throwable("Original registration for " + name));
    }
  }

  @Override
  public void registerAreaExtensionsAndPoints(final PluginDescriptor pluginDescriptor,
                                              final List<Element> extensionsPoints,
                                              final List<Element> extensions) {
    final String areaClass = getAreaClass();
    if (extensionsPoints != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, size = extensionsPoints.size(); i < size; ++i) {
        Element element = extensionsPoints.get(i);
        if (equal(areaClass, element.getAttributeValue(ATTRIBUTE_AREA))) {
          registerExtensionPoint(pluginDescriptor, element);
        }
      }
    }

    if (extensions != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0, size = extensions.size(); i < size; ++i) {
        Element element = extensions.get(i);
        if (hasExtensionPoint(extractEPName(element))) {
          registerExtension(pluginDescriptor, element);
        }
        else {
          //todo check that other classes have EP
        }
      }
    }
  }

  private static boolean equal(final String areaClass, final String anotherAreaClass) {
    return areaClass == null ? anotherAreaClass == null : areaClass.equals(anotherAreaClass);
  }

  @SuppressWarnings({"unchecked"})
  private void notifyEPRegistered(final ExtensionPoint extensionPoint) {
    Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(extensionPoint.getName());
    for (final ExtensionPointAvailabilityListener listener : listeners) {
      notifyAvailableListener(listener, extensionPoint);
    }
  }

  private void notifyAvailableListener(final ExtensionPointAvailabilityListener listener, final ExtensionPoint extensionPoint) {
    queueNotificationAction(new Runnable() {
      @Override
      public void run() {
        listener.extensionPointRegistered(extensionPoint);
      }
    });
  }

  private void queueNotificationAction(final Runnable action) {
    if (myAvailabilityNotificationsActive) {
      action.run();
    }
    else {
      mySuspendedListenerActions.add(action);
    }
  }

  @Override
  @NotNull
  public <T> ExtensionPointImpl<T> getExtensionPoint(String extensionPointName) {
    ExtensionPointImpl<T> extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint == null) {
      throw new IllegalArgumentException("Missing extension point: " + extensionPointName + " in area " + myAreaInstance);
    }
    return extensionPoint;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> ExtensionPoint<T> getExtensionPoint(ExtensionPointName<T> extensionPointName) {
    return getExtensionPoint(extensionPointName.getName());
  }

  @Override
  public ExtensionPoint[] getExtensionPoints() {
    return myExtensionPoints.values().toArray(new ExtensionPoint[myExtensionPoints.size()]);
  }

  @Override
  public void unregisterExtensionPoint(final String extensionPointName) {
    ExtensionPoint extensionPoint = myExtensionPoints.get(extensionPointName);
    if (extensionPoint != null) {
      extensionPoint.reset();
      myExtensionPoints.remove(extensionPointName);
      notifyEPRemoved(extensionPoint);
    }
  }

  @SuppressWarnings({"unchecked"})
  private void notifyEPRemoved(final ExtensionPoint extensionPoint) {
    Collection<ExtensionPointAvailabilityListener> listeners = myAvailabilityListeners.get(extensionPoint.getName());
    for (final ExtensionPointAvailabilityListener listener : listeners) {
      notifyUnavailableListener(extensionPoint, listener);
    }
  }

  private void notifyUnavailableListener(final ExtensionPoint extensionPoint, final ExtensionPointAvailabilityListener listener) {
    queueNotificationAction(new Runnable() {
      @Override
      public void run() {
        listener.extensionPointRemoved(extensionPoint);
      }
    });
  }

  @Override
  public boolean hasExtensionPoint(String extensionPointName) {
    return myExtensionPoints.containsKey(extensionPointName);
  }

  @Override
  public void suspendInteractions() {
    myAvailabilityNotificationsActive = false;
  }

  @Override
  public void resumeInteractions() {
    myAvailabilityNotificationsActive = true;
    ExtensionPoint[] extensionPoints = getExtensionPoints();
    for (ExtensionPoint extensionPoint : extensionPoints) {
      extensionPoint.getExtensions(); // creates extensions from ComponentAdapters
    }
    for (Runnable action : mySuspendedListenerActions) {
      try {
        action.run();
      }
      catch (Exception e) {
        myLogger.error(e);
      }
    }
    mySuspendedListenerActions.clear();
  }

  @Override
  public void killPendingInteractions() {
    mySuspendedListenerActions.clear();
  }

  public MutablePicoContainer[] getPluginContainers() {
    return myPluginName2picoContainer.values().toArray(new MutablePicoContainer[myPluginName2picoContainer.values().size()]);
  }

  public void removeAllComponents(final Set<ExtensionComponentAdapter> extensionAdapters) {
    for (final Object extensionAdapter : extensionAdapters) {
      ExtensionComponentAdapter componentAdapter = (ExtensionComponentAdapter)extensionAdapter;
      internalGetPluginContainer().unregisterComponent(componentAdapter.getComponentKey());
    }
  }

}

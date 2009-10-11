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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 27, 2002
 * Time: 2:57:13 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

@State(
    name = "EntryPointsManager",
    storages = {@Storage(id = "default", file = "$PROJECT_FILE$")}
)
public class EntryPointsManagerImpl implements PersistentStateComponent<Element>, EntryPointsManager, Disposable {
  private final Map<String, SmartRefElementPointer> myPersistentEntryPoints;
  private final Set<RefElement> myTemporaryEntryPoints;
  private static final String VERSION = "2.0";
  @NonNls private static final String VERSION_ATTR = "version";
  @NonNls private static final String ENTRY_POINT_ATTR = "entry_point";
  private boolean myAddNonJavaEntries = true;
  private boolean myResolved = false;
  private final Project myProject;
  private long myLastModificationCount = -1;

  public EntryPointsManagerImpl(Project project) {
    myProject = project;
    myTemporaryEntryPoints = new HashSet<RefElement>();
    myPersistentEntryPoints =
        new LinkedHashMap<String, SmartRefElementPointer>(); // To keep the order between readExternal to writeExternal
  }

  public static EntryPointsManagerImpl getInstance(Project project) {
    return ServiceManager.getService(project, EntryPointsManagerImpl.class);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void loadState(Element element) {
    Element entryPointsElement = element.getChild("entry_points");
    final String version = entryPointsElement.getAttributeValue(VERSION_ATTR);
    if (!Comparing.strEqual(version, VERSION)) {
      convert(entryPointsElement);
    }
    else {
      List content = entryPointsElement.getChildren();
      for (final Object aContent : content) {
        Element entryElement = (Element)aContent;
        if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
          SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(entryElement);
          myPersistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
        }
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState()  {
    Element element = new Element("state");
    writeExternal(element);
    return element;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(final Element element) {
    Element entryPointsElement = new Element("entry_points");
    entryPointsElement.setAttribute(VERSION_ATTR, VERSION);
    for (SmartRefElementPointer entryPoint : myPersistentEntryPoints.values()) {
      assert entryPoint.isPersistent();
      entryPoint.writeExternal(entryPointsElement);
    }

    element.addContent(entryPointsElement);
  }

  public void resolveEntryPoints(final RefManager manager) {
    if (!myResolved) {
      myResolved = true;
      validateEntryPoints();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (SmartRefElementPointer entryPoint : myPersistentEntryPoints.values()) {
            if (entryPoint.resolve(manager)) {
              RefEntity refElement = entryPoint.getRefElement();
              ((RefElementImpl)refElement).setEntry(true);
              ((RefElementImpl)refElement).setPermanentEntry(entryPoint.isPersistent());
            }
          }
        }
      });
    }
  }

  private void purgeTemporaryEntryPoints() {
    for (RefElement entryPoint : myTemporaryEntryPoints) {
      ((RefElementImpl)entryPoint).setEntry(false);
    }

    myTemporaryEntryPoints.clear();
  }

  public void addEntryPoint(RefElement newEntryPoint, boolean isPersistent) {
    if (!newEntryPoint.isValid()) return;
    if (newEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass)newEntryPoint;

      if (refClass.isAnonymous()) {
        // Anonymous class cannot be an entry point.
        return;
      }

      ArrayList<RefMethod> refConstructors = refClass.getConstructors();
      if (refConstructors.size() == 1) {
        addEntryPoint(refConstructors.get(0), isPersistent);
        return;
      }
      else if (refConstructors.size() > 1) {
        // Many constructors here. Need to ask user which ones are used
        for (int i = 0; i < refConstructors.size(); i++) {
          addEntryPoint((RefMethod)((ArrayList)refConstructors).get(i), isPersistent);
        }

        return;
      }
    }

    if (isPersistent) {
      myTemporaryEntryPoints.add(newEntryPoint);
      ((RefElementImpl)newEntryPoint).setEntry(true);
    }
    else {
      if (myPersistentEntryPoints.get(newEntryPoint.getExternalName()) == null) {
        final SmartRefElementPointerImpl entry = new SmartRefElementPointerImpl(newEntryPoint, true);
        myPersistentEntryPoints.put(entry.getFQName(), entry);
        ((RefElementImpl)newEntryPoint).setEntry(true);
        ((RefElementImpl)newEntryPoint).setPermanentEntry(true);
        if (entry.isPersistent()) { //do save entry points
          final EntryPointsManagerImpl entryPointsManager = EntryPointsManagerImpl.getInstance(newEntryPoint.getElement().getProject());
          if (this != entryPointsManager) {
            entryPointsManager.addEntryPoint(newEntryPoint, true);
          }
        }
      }
    }
  }

  public void removeEntryPoint(RefElement anEntryPoint) {
    if (anEntryPoint instanceof RefClass) {
      RefClass refClass = (RefClass)anEntryPoint;
      if (!refClass.isInterface()) {
        anEntryPoint = refClass.getDefaultConstructor();
      }
    }

    if (anEntryPoint == null) return;

    myTemporaryEntryPoints.remove(anEntryPoint);

    Set<Map.Entry<String, SmartRefElementPointer>> set = myPersistentEntryPoints.entrySet();
    String key = null;
    for (Map.Entry<String, SmartRefElementPointer> entry : set) {
      SmartRefElementPointer value = entry.getValue();
      if (value.getRefElement() == anEntryPoint) {
        key = entry.getKey();
        break;
      }
    }

    if (key != null) {
      myPersistentEntryPoints.remove(key);
      ((RefElementImpl)anEntryPoint).setEntry(false);
    }

    if (anEntryPoint.isPermanentEntry() && anEntryPoint.isValid()) {
      final Project project = anEntryPoint.getElement().getProject();
      final EntryPointsManagerImpl entryPointsManager = EntryPointsManagerImpl.getInstance(project);
      if (this != entryPointsManager) {
        entryPointsManager.removeEntryPoint(anEntryPoint);
      }
    }
  }

  public RefElement[] getEntryPoints() {
    validateEntryPoints();
    List<RefElement> entries = new ArrayList<RefElement>();
    Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
    for (SmartRefElementPointer refElementPointer : collection) {
      final RefEntity elt = refElementPointer.getRefElement();
      if (elt instanceof RefElement) {
        entries.add((RefElement)elt);
      }
    }
    entries.addAll(myTemporaryEntryPoints);

    return entries.toArray(new RefElement[entries.size()]);
  }

  public void dispose() {
    cleanup();
  }

  private void validateEntryPoints() {
    long count = PsiManager.getInstance(myProject).getModificationTracker().getModificationCount();
    if (count != myLastModificationCount) {
      myLastModificationCount = count;
      Collection<SmartRefElementPointer> collection = myPersistentEntryPoints.values();
      SmartRefElementPointer[] entries = collection.toArray(new SmartRefElementPointer[collection.size()]);
      for (SmartRefElementPointer entry : entries) {
        RefElement refElement = (RefElement)entry.getRefElement();
        if (refElement != null && !refElement.isValid()) {
          myPersistentEntryPoints.remove(entry.getFQName());
        }
      }

      final Iterator<RefElement> it = myTemporaryEntryPoints.iterator();
      while (it.hasNext()) {
        RefElement refElement = it.next();
        if (!refElement.isValid()) {
          it.remove();
        }
      }
    }
  }

  public void cleanup() {
    purgeTemporaryEntryPoints();
    Collection<SmartRefElementPointer> entries = myPersistentEntryPoints.values();
    for (SmartRefElementPointer entry : entries) {
      entry.freeReference();
    }
  }

  public boolean isAddNonJavaEntries() {
    return myAddNonJavaEntries;
  }

  public void addAllPersistentEntries(EntryPointsManagerImpl manager) {
    myPersistentEntryPoints.putAll(manager.myPersistentEntryPoints);
  }

  public void convert(Element element) {
    List content = element.getChildren();
    for (final Object aContent : content) {
      Element entryElement = (Element)aContent;
      if (ENTRY_POINT_ATTR.equals(entryElement.getName())) {
        String fqName = entryElement.getAttributeValue(SmartRefElementPointerImpl.FQNAME_ATTR);
        final String type = entryElement.getAttributeValue(SmartRefElementPointerImpl.TYPE_ATTR);
        if (Comparing.strEqual(type, RefJavaManager.METHOD)) {

          int spaceIdx = fqName.indexOf(' ');
          int lastDotIdx = fqName.lastIndexOf('.');
          boolean notype = false;

          int parenIndex = fqName.indexOf('(');

          while (lastDotIdx > parenIndex) lastDotIdx = fqName.lastIndexOf('.', lastDotIdx - 1);

          if (spaceIdx < 0 || spaceIdx + 1 > lastDotIdx || spaceIdx > parenIndex) {
            notype = true;
          }

          final String className = fqName.substring(notype ? 0 : spaceIdx + 1, lastDotIdx);
          final String methodSignature =
              notype ? fqName.substring(lastDotIdx + 1) : fqName.substring(0, spaceIdx) + ' ' + fqName.substring(lastDotIdx + 1);

          fqName = className + " " + methodSignature;
        }
        else if (Comparing.strEqual(type, RefJavaManager.FIELD)) {
          final int lastDotIdx = fqName.lastIndexOf('.');
          if (lastDotIdx > 0 && lastDotIdx < fqName.length() - 2) {
            String className = fqName.substring(0, lastDotIdx);
            String fieldName = fqName.substring(lastDotIdx + 1);
            fqName = className + " " + fieldName;
          }
          else {
            continue;
          }
        }
        SmartRefElementPointerImpl entryPoint = new SmartRefElementPointerImpl(type, fqName);
        myPersistentEntryPoints.put(entryPoint.getFQName(), entryPoint);
      }
    }
  }

  public void setAddNonJavaEntries(final boolean addNonJavaEntries) {
    myAddNonJavaEntries = addNonJavaEntries;
  }
}

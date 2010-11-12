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
package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

public class CompositeAppearance implements ModifiableCellAppearance {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.util.CompositeAppearance");

  private Icon myIcon;
  private final ArrayList<TextSection> mySections = new ArrayList<TextSection>();
  private int myInsertionIndex = 0;

  public synchronized void customize(SimpleColoredComponent component) {
    for (TextSection section : mySections) {
      TextAttributes attributes = section.ATTRIBUTES;
      component.append(section.TEXT, SimpleTextAttributes.fromTextAttributes(attributes));
    }
    component.setIcon(myIcon);
  }

  public Icon getIcon() {
    return myIcon;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public synchronized String getText() {
    StringBuilder buffer = new StringBuilder();
    for (TextSection section : mySections) {
      buffer.append(section.TEXT);
    }
    return buffer.toString();
  }

  public synchronized boolean equals(Object obj) {
    if (!(obj instanceof CompositeAppearance)) return false;
    CompositeAppearance appearance = (CompositeAppearance)obj;
    if (SwingUtilities.isEventDispatchThread()) {
      return appearance.mySections.equals(mySections);
    }
    else {
      return new ArrayList<TextSection>(appearance.mySections).equals(new ArrayList<TextSection>(mySections));
    }
  }

  public int hashCode() {
    return getText().hashCode();
  }

  protected void addSectionAt(int index, @NotNull TextSection section) {
    synchronized (this) {
      mySections.add(index, section);
      for (Iterator<TextSection> iterator = mySections.iterator(); iterator.hasNext();) {
        TextSection textSection = iterator.next();
        if (textSection == null) {
          LOG.error("index: " + index + " size: " + mySections.size());
          iterator.remove();
        }
      }
    }
  }

  public DequeEnd getBeginning() {
    return new DequeBeginning();
  }

  public DequeEnd getEnding() {
    return new DequeEnding();
  }

  public DequeEnd getSuffix() {
    return new DequeSuffix();
  }

  public static CompositeAppearance textComment(String text, String comment) {
    DequeEnd ending = new CompositeAppearance().getEnding();
    ending.addText(text);
    ending.addComment(comment);
    return ending.getAppearance();
  }

  public static CompositeAppearance single(String text, SimpleTextAttributes textAttributes) {
    CompositeAppearance result = new CompositeAppearance();
    result.getEnding().addText(text, textAttributes);
    return result;
  }

  public static CompositeAppearance single(String text) {
    return single(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public static CompositeAppearance invalid(String absolutePath) {
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.setIcon(CellAppearanceUtils.INVALID_ICON);
    appearance.getEnding().addText(absolutePath, SimpleTextAttributes.ERROR_ATTRIBUTES);
    return appearance;
  }

  public Iterator<TextSection> getSectionsIterator() {
    return mySections.iterator();
  }

  public static class TextSection {
    private static final TextAttributes DEFAULT_TEXT_ATTRIBUTES = new TextAttributes(null, null, null, null, Font.PLAIN);
    private static final String DEFAULT_TEXT = "";
    private String TEXT;
    private TextAttributes ATTRIBUTES;

    public TextSection(String text, TextAttributes attributes) {
      ATTRIBUTES = attributes == null ? DEFAULT_TEXT_ATTRIBUTES : attributes;
      TEXT = text == null ? DEFAULT_TEXT : text;
    }

    public String getText() {
      return TEXT;
    }

    public TextAttributes getTextAttributes() {
      return ATTRIBUTES;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof TextSection)) return false;
      TextSection section = (TextSection)obj;
      return section.ATTRIBUTES.equals(ATTRIBUTES) && section.TEXT.equals(TEXT);
    }

    public int hashCode() {
      return TEXT.hashCode();
    }
  }

  public abstract class DequeEnd {
    public void addText(String text, SimpleTextAttributes textAttributes) {
      addText(text, textAttributes.toTextAttributes());
    }

    public void addText(String text) {
      addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    public abstract void addSection(TextSection section);

    public void addText(String text, TextAttributes attributes) {
      addSection(new TextSection(text, attributes));
    }

    public void addSurrounded(String text, String prefix, String suffix, SimpleTextAttributes textAttributes) {
      if (text != null && text.trim().length() > 0) {
        addText(prefix + text + suffix, textAttributes);
      }
    }

    public CompositeAppearance getAppearance() {
      return CompositeAppearance.this;
    }

    public void addComment(String comment, SimpleTextAttributes commentAttributes) {
      addSurrounded(comment, " (", ")", commentAttributes);
    }

    public void addComment(String comment) {
      addComment(comment, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private class DequeBeginning extends DequeEnd {
    public void addSection(TextSection section) {
      synchronized (CompositeAppearance.this) {
        addSectionAt(0, section);
        myInsertionIndex++;
      }
    }
  }

  private class DequeEnding extends DequeEnd {
    public void addSection(TextSection section) {
      synchronized (CompositeAppearance.this) {
        addSectionAt(myInsertionIndex, section);
        myInsertionIndex++;
      }
    }
  }

  private class DequeSuffix extends DequeEnd {
    public void addSection(TextSection section) {
      synchronized (CompositeAppearance.this) {
        addSectionAt(mySections.size(), section);
      }
    }
  }
}

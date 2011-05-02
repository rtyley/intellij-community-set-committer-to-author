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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.CreateNSDeclarationIntentionFix;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author spleaner
 */
public class HtmlUnknownTagInspection extends HtmlLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection");

  public JDOMExternalizableStringList myValues;
  public boolean myCustomValuesEnabled = true;
  @NonNls public static final String TAG_SHORT_NAME = "HtmlUnknownTag";

  public HtmlUnknownTagInspection() {
    this("nobr,noembed,comment,noscript,embed,script");
  }

  protected HtmlUnknownTagInspection(@NonNls @NotNull final String defaultValues) {
    myValues = reparseProperties(defaultValues);
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspections.unknown.tag");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return TAG_SHORT_NAME;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel result = new JPanel(new BorderLayout());

    final JPanel internalPanel = new JPanel();
    internalPanel.setLayout(new BoxLayout(internalPanel, BoxLayout.Y_AXIS));

    result.add(internalPanel, BorderLayout.SOUTH);

    final FieldPanel additionalAttributesPanel = new FieldPanel(null, getPanelTitle(), null, null);
    additionalAttributesPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        final Document document = e.getDocument();
        try {
          final String text = document.getText(0, document.getLength());
          if (text != null) {
            myValues = reparseProperties(text.trim());
          }
        }
        catch (BadLocationException e1) {
          getLogger().error(e1);
        }
      }
    });

    final JCheckBox checkBox = new JCheckBox(getCheckboxTitle());
    checkBox.setSelected(myCustomValuesEnabled);
    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final boolean b = checkBox.isSelected();
        if (b != myCustomValuesEnabled) {
          myCustomValuesEnabled = b;
          additionalAttributesPanel.setEnabled(myCustomValuesEnabled);
        }
      }
    });

    internalPanel.add(checkBox);
    internalPanel.add(additionalAttributesPanel);

    additionalAttributesPanel.setPreferredSize(new Dimension(150, additionalAttributesPanel.getPreferredSize().height));
    additionalAttributesPanel.setEnabled(myCustomValuesEnabled);
    additionalAttributesPanel.setText(createPropertiesString());

    return result;
  }

  @NotNull
  protected Logger getLogger() {
    return LOG;
  }

  private String createPropertiesString() {
    final StringBuffer buffer = new StringBuffer();
    for (final String property : myValues) {
      if (buffer.length() == 0) {
        buffer.append(property);
      }
      else {
        buffer.append(',');
        buffer.append(property);
      }
    }

    return buffer.toString();
  }

  public String getAdditionalEntries() {
    return createPropertiesString();
  }

  protected String getCheckboxTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.checkbox.title");
  }

  protected static JDOMExternalizableStringList reparseProperties(@NotNull final String properties) {
    final JDOMExternalizableStringList result = new JDOMExternalizableStringList();

    final StringTokenizer tokenizer = new StringTokenizer(properties, ",");
    while (tokenizer.hasMoreTokens()) {
      result.add(tokenizer.nextToken().toLowerCase().trim());
    }

    return result;
  }

  public void setAdditionalValues(@NotNull final String values) {
    myValues = reparseProperties(values);
  }

  protected String getPanelTitle() {
    return XmlBundle.message("html.inspections.unknown.tag.title");
  }

  protected boolean isCustomValue(@NotNull final String value) {
    return myValues.contains(value.toLowerCase());
  }

  public void addCustomPropertyName(@NotNull final String text) {
    final String s = text.trim().toLowerCase();
    if (!isCustomValue(s)) {
      myValues.add(s);
    }

    if (!isCustomValuesEnabled()) {
      myCustomValuesEnabled = true;
    }
  }

  public boolean isCustomValuesEnabled() {
    return myCustomValuesEnabled;
  }

  private static boolean isAbstractDescriptor(XmlElementDescriptor descriptor) {
    return descriptor == null || descriptor instanceof AnyXmlElementDescriptor;
  }

  // this hack is needed, because we temporarily ignore svg and mathML namespaces
  // todo: provide schemas for svg and mathML and remove this in IDEA XI
  private static boolean isInSpecialHtml5Namespace(XmlTag tag) {
    final String ns = tag.getNamespace();
    return HtmlUtil.SVG_NAMESPACE.equals(ns) || HtmlUtil.MATH_ML_NAMESPACE.endsWith(ns);
  }

  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    if (!(tag instanceof HtmlTag) || !XmlHighlightVisitor.shouldBeValidated(tag) || isInSpecialHtml5Namespace(tag)) {
      return;
    }

    XmlElementDescriptor descriptorFromContext = XmlUtil.getDescriptorFromContext(tag);

    PsiElement parent = tag.getParent();
    XmlElementDescriptor parentDescriptor = parent instanceof XmlTag ? ((XmlTag)parent).getDescriptor() : null;

    XmlElementDescriptor ownDescriptor = isAbstractDescriptor(descriptorFromContext)
                                         ? tag.getDescriptor()
                                         : descriptorFromContext;

    if (isAbstractDescriptor(ownDescriptor) ||
        (parentDescriptor instanceof HtmlElementDescriptorImpl &&
         ownDescriptor instanceof HtmlElementDescriptorImpl &&
         isAbstractDescriptor(descriptorFromContext))) {

      final String name = tag.getName();

      if (!isCustomValuesEnabled() || !isCustomValue(name)) {
        final AddCustomTagOrAttributeIntentionAction action =
          new AddCustomTagOrAttributeIntentionAction(getShortName(), name, XmlEntitiesInspection.UNKNOWN_TAG);

        // todo: support "element is not allowed" message for html5
        // some tags in html5 cannot be found in xhtml5.xsd if they are located in incorrect context, so they get any-element descriptor (ex. "canvas: tag)
        final String message = isAbstractDescriptor(ownDescriptor)
                               ? XmlErrorMessages.message("unknown.html.tag", name)
                               : XmlErrorMessages.message("element.is.not.allowed.here", name);

        final PsiElement startTagName = XmlTagUtil.getStartTagNameElement(tag);
        assert startTagName != null;
        final PsiElement endTagName = XmlTagUtil.getEndTagNameElement(tag);

        List<LocalQuickFix> quickfixes = new ArrayList<LocalQuickFix>();
        quickfixes.add(action);
        if (isOnTheFly) {
          ContainerUtil.addIfNotNull(CreateNSDeclarationIntentionFix.createFix(startTagName, ""), quickfixes);
        }
        if (HtmlUtil.isHtml5Tag(name) && !HtmlUtil.hasNonHtml5Doctype(tag)) {
          quickfixes.add(new SwitchToHtml5WithHighPriorityAction());
        }
        ProblemHighlightType highlightType = tag.getContainingFile().getContext() == null ?
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
                                             ProblemHighlightType.INFORMATION;
        holder.registerProblem(startTagName, message, highlightType, quickfixes.toArray(new LocalQuickFix[quickfixes.size()]));

        if (endTagName != null) {
          holder.registerProblem(endTagName, message, highlightType, quickfixes.toArray(new LocalQuickFix[quickfixes.size()]));
        }
      }
    }
  }
}

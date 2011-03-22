/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceParameter.AbstractInplaceIntroducer;
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.ui.JavaVisibilityPanel;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.TitlePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: anna
 * Date: 3/18/11
 */
public class InplaceIntroduceConstantPopup {
  private final Project myProject;
  private final PsiClass myParentClass;
  private PsiExpression myExpr;
  private final PsiLocalVariable myLocalVariable;
  private final PsiExpression[] myOccurrences;
  private final TypeSelectorManagerImpl myTypeSelectorManager;
  private final PsiElement myAnchorElement;
  private final PsiElement myAnchorElementIfAll;
  private final OccurenceManager myOccurenceManager;

  private Editor myEditor;
  private String myConstantName;
  private List<RangeMarker> myOccurrenceMarkers;
  private final String myExprText;
  private final String myLocalName;
  private RangeMarker myExprMarker;

  private boolean myInitListeners = false;
  private JCheckBox myReplaceAllCb;
  private JCheckBox myAnnotateNonNls;
  private StateRestoringCheckBox myCbDeleteVariable;

  private JCheckBox myMoveToAnotherClassCb;

  private JavaVisibilityPanel myVisibilityPanel;

  private JPanel myWholePanel;

  public InplaceIntroduceConstantPopup(Project project,
                                       Editor editor,
                                       PsiClass parentClass,
                                       PsiExpression expr,
                                       PsiLocalVariable localVariable,
                                       PsiExpression[] occurrences,
                                       TypeSelectorManagerImpl typeSelectorManager,
                                       PsiElement anchorElement,
                                       PsiElement anchorElementIfAll, OccurenceManager occurenceManager) {
    myProject = project;
    myEditor = editor;
    myParentClass = parentClass;
    myExpr = expr;
    myLocalVariable = localVariable;
    myOccurrences = occurrences;
    myTypeSelectorManager = typeSelectorManager;
    myAnchorElement = anchorElement;
    myAnchorElementIfAll = anchorElementIfAll;
    myOccurenceManager = occurenceManager;

    myExprMarker = expr != null ? myEditor.getDocument().createRangeMarker(expr.getTextRange()) : null;
    myExprText = expr != null ? expr.getText() : null;
    myLocalName = localVariable != null ? localVariable.getName() : null;

    myWholePanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0 , 0, 0), 0, 0);

    final TitlePanel titlePanel = new TitlePanel();
    titlePanel.setBorder(null);
    titlePanel.setText(IntroduceConstantHandler.REFACTORING_NAME);

    myWholePanel.add(titlePanel, gc);

    gc.insets = new Insets(5, 5, 5, 5);

    myVisibilityPanel = new JavaVisibilityPanel(false, true);
    myVisibilityPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);
    myWholePanel.add(myVisibilityPanel, gc);

    myReplaceAllCb = new JCheckBox("Replace all occurrences");
    myReplaceAllCb.setMnemonic('a');
    myReplaceAllCb.setFocusable(false);
    myWholePanel.add(myReplaceAllCb, gc);
    myReplaceAllCb.setVisible(myOccurrences.length > 1);


    myCbDeleteVariable = new StateRestoringCheckBox("Delete variable declaration");
    myCbDeleteVariable.setMnemonic('d');
    myCbDeleteVariable.setFocusable(false);
    myWholePanel.add(myCbDeleteVariable, gc);
    if (myLocalVariable != null) {
      if (myReplaceAllCb != null) {
        myReplaceAllCb.setEnabled(false);
        myReplaceAllCb.setSelected(true);
        myCbDeleteVariable.setSelected(true);
        myCbDeleteVariable.setEnabled(false);
      }
    } else {
      myCbDeleteVariable.setVisible(false);
    }

    myAnnotateNonNls = new JCheckBox("Annotate field as @NonNls");
    myAnnotateNonNls.setMnemonic('f');
    myAnnotateNonNls.setFocusable(false);
    myWholePanel.add(myAnnotateNonNls, gc);
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if ((myTypeSelectorManager.isSuggestedType("java.lang.String") || (myLocalVariable != null && AnnotationUtil.isAnnotated(myLocalVariable, AnnotationUtil.NON_NLS, false)))&&
        LanguageLevelProjectExtension.getInstance(psiManager.getProject()).getLanguageLevel().hasEnumKeywordAndAutoboxing() &&
        JavaPsiFacade.getInstance(psiManager.getProject()).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myAnnotateNonNls.setSelected(component.isTrueValue(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY));
      myAnnotateNonNls.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          component.setValue(IntroduceConstantDialog.NONNLS_SELECTED_PROPERTY, Boolean.toString(myAnnotateNonNls.isSelected()));
        }
      });
    } else {
      myAnnotateNonNls.setVisible(false);
    }

    myMoveToAnotherClassCb = new JCheckBox("Move to another class");
    myMoveToAnotherClassCb.setMnemonic('m');
    myMoveToAnotherClassCb.setFocusable(false);
    myWholePanel.add(myMoveToAnotherClassCb, gc);


  }

  public void performInplaceIntroduce() {
    startIntroduceTemplate(false);
  }

  private void startIntroduceTemplate(final boolean replaceAllOccurrences) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        myTypeSelectorManager.setAllOccurences(replaceAllOccurrences);
        final PsiType defaultType = myTypeSelectorManager.getTypeSelector().getSelectedType();
        final String propName = myLocalVariable != null ? JavaCodeStyleManager
          .getInstance(myProject).variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE) : null;
        final String[] names = IntroduceConstantDialog.createNameSuggestionGenerator(propName, myExpr, JavaCodeStyleManager.getInstance(myProject))
          .getSuggestedNameInfo(defaultType).names;
        final PsiField field = createFieldToStartTemplateOn(names, defaultType);
        if (field != null) {
          myEditor.getCaretModel().moveToOffset(field.getTextOffset());
          final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<String>();
          nameSuggestions.add(field.getName());
          nameSuggestions.addAll(Arrays.asList(names));
          final VariableInplaceRenamer renamer = new FieldInplaceIntroducer(field);
          renamer.performInplaceRename(false, nameSuggestions);
        }
      }
    }, IntroduceConstantHandler.REFACTORING_NAME, null);
  }

  private PsiField createFieldToStartTemplateOn(final String[] names, final PsiType psiType) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiField>() {
      @Override
      public PsiField compute() {
        PsiField field = elementFactory.createField(myConstantName != null ? myConstantName : names[0], psiType);
        field = (PsiField)myParentClass.add(field);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
        return field;
      }
    });
  }

  public List<RangeMarker> getOccurrenceMarkers() {
    if (myOccurrenceMarkers == null) {
      myOccurrenceMarkers = new ArrayList<RangeMarker>();
      for (PsiExpression occurrence : myOccurrences) {
        myOccurrenceMarkers.add(myEditor.getDocument().createRangeMarker(occurrence.getTextRange()));
      }
    }
    return myOccurrenceMarkers;
  }

  private void updateCbDeleteVariable() {
    if (!myReplaceAllCb.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    }
    else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private class FieldInplaceIntroducer extends AbstractInplaceIntroducer {
    private RangeMarker myFieldRangeStart;


    private SmartTypePointer myDefaultParameterTypePointer;

    private SmartTypePointer myFieldTypePointer;

    public FieldInplaceIntroducer(PsiField field) {
      super(myProject, new TypeExpression(myProject, myTypeSelectorManager.getTypesForAll()),
            myEditor, field, false,
            myTypeSelectorManager.getTypesForAll().length > 1,
            myExpr != null ? myEditor.getDocument().createRangeMarker(myExpr.getTextRange()) : null, InplaceIntroduceConstantPopup.this.getOccurrenceMarkers());

      myDefaultParameterTypePointer =
        SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(myTypeSelectorManager.getDefaultType());
      myFieldRangeStart = myEditor.getDocument().createRangeMarker(field.getTextRange());
    }

    @Override
    protected boolean isReplaceAllOccurrences() {
      return myReplaceAllCb.isSelected();
    }

    @Override
    protected PsiExpression getExpr() {
      return myExpr;
    }

    @Override
    protected PsiExpression[] getOccurrences() {
      return myOccurrences;
    }

    @Override
    protected List<RangeMarker> getOccurrenceMarkers() {
      return InplaceIntroduceConstantPopup.this.getOccurrenceMarkers();
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myParentClass;
    }

    @Override
    public RangeMarker getExprMarker() {
      return myExprMarker;
    }

    @Override
    protected void saveSettings(PsiVariable psiVariable) {
      TypeSelectorManagerImpl.typeSelected(psiVariable.getType(), myDefaultParameterTypePointer.getType());
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = myVisibilityPanel.getVisibility();
    }

    @Override
    protected PsiVariable getVariable() {
      PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
      if (element instanceof PsiWhiteSpace) {
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
      }
      return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    }

    @Override
    protected void moveOffsetAfter(boolean success) {
      if (success) {
        final BaseExpressionToFieldHandler.Settings settings =
          new BaseExpressionToFieldHandler.Settings(myConstantName,
                                                    isReplaceAllOccurrences(), true,
                                                    true,
                                                    BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                    myVisibilityPanel.getVisibility(), myLocalVariable,
                                                    myFieldTypePointer.getType(),
                                                    isDeleteVariable(),
                                                    myParentClass, isAnnotateNonNls(), false);
        if (myLocalVariable != null) {
          final LocalToFieldHandler.IntroduceFieldRunnable fieldRunnable =
            new LocalToFieldHandler.IntroduceFieldRunnable(false, myLocalVariable, myParentClass, settings, true, myOccurrences);
          fieldRunnable.run();
        }
        else {
          final BaseExpressionToFieldHandler.ConvertToFieldRunnable convertToFieldRunnable =
            new BaseExpressionToFieldHandler.ConvertToFieldRunnable(myExpr, settings, settings.getForcedType(),
                                                                    myOccurrences, myOccurenceManager,
                                                                    myAnchorElementIfAll, myAnchorElement, myEditor,
                                                                    myParentClass);
          convertToFieldRunnable.run();
        }
      }
      super.moveOffsetAfter(success);
      if (myMoveToAnotherClassCb.isSelected()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            MoveMembersImpl.doMove(myProject, new PsiElement[]{myParentClass.findFieldByName(myConstantName, false)}, null, null);
          }
        });
      }
    }

    @Override
    protected JComponent getComponent() {
      if (!myInitListeners) {
        myInitListeners = true;
        myVisibilityPanel.addListener(new VisibilityListener(myProject, myEditor){
          @Override
          protected String getVisibility() {
            return myVisibilityPanel.getVisibility();
          }
        });
        myReplaceAllCb.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
            if (templateState != null) {
              templateState.gotoEnd(true);
              startIntroduceTemplate(isReplaceAllOccurrences());
            }
          }
        });

        myAnnotateNonNls.addItemListener(new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            //todo it is unresolved; import here is not a good idea new FinalListener(myProject).perform(myAnnotateNonNls.isSelected(), "@NonNls");
          }
        });
      }
      return myWholePanel;
    }

    @Override
    public void finish() {
      super.finish();
      final PsiField psiField = (PsiField)getVariable();
      LOG.assertTrue(psiField != null);
      myFieldTypePointer = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(psiField.getType());
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      myConstantName = psiField.getName();

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final PsiFile containingFile = myParentClass.getContainingFile();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
          myExpr = restoreExpression(containingFile, psiField, elementFactory, getExprMarker(), myExprText);
          if (myExpr != null) {
            myExprMarker = myEditor.getDocument().createRangeMarker(myExpr.getTextRange());
          }
          final List<RangeMarker> occurrenceMarkers = getOccurrenceMarkers();
          for (int i = 0, occurrenceMarkersSize = occurrenceMarkers.size(); i < occurrenceMarkersSize; i++) {
            RangeMarker marker = occurrenceMarkers.get(i);
            if (getExprMarker() != null && marker.getStartOffset() == getExprMarker().getStartOffset()) {
              myOccurrences[i] = myExpr;
              continue;
            }
            final PsiExpression psiExpression = restoreExpression(containingFile, psiField, elementFactory, marker, myLocalVariable != null ? myLocalName : myExprText);
            if (psiExpression != null) {
              myOccurrences[i] = psiExpression;
            }
          }
          myOccurrenceMarkers = null;
          if (psiField.isValid()) {
            psiField.delete();
          }
        }
      });
    }
  }

  private boolean isAnnotateNonNls() {
    return myAnnotateNonNls.isSelected();
  }

  private boolean isDeleteVariable() {
    return myCbDeleteVariable.isSelected();
  }
}

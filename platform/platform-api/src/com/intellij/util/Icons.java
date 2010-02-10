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
package com.intellij.util;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class Icons {
  @NonNls public static final String PUBLIC_ICON_PATH = "/nodes/c_public.png";
  public static final Icon PUBLIC_ICON = IconLoader.getIcon(PUBLIC_ICON_PATH);
  public static final Icon LOCKED_ICON = IconLoader.getIcon("/nodes/locked.png");
  public static final Icon PRIVATE_ICON = IconLoader.getIcon("/nodes/c_private.png");
  public static final Icon PROTECTED_ICON = IconLoader.getIcon("/nodes/c_protected.png");
  public static final Icon PACKAGE_LOCAL_ICON = IconLoader.getIcon("/nodes/c_plocal.png");
  public static final Icon PACKAGE_ICON = IconLoader.getIcon("/nodes/packageClosed.png");
  public static final Icon PACKAGE_OPEN_ICON = IconLoader.getIcon("/nodes/packageOpen.png");
  public static final Icon DIRECTORY_CLOSED_ICON = IconLoader.getIcon("/nodes/TreeClosed.png");
  public static final Icon DIRECTORY_OPEN_ICON = IconLoader.getIcon("/nodes/TreeOpen.png");
  @NonNls public static final String CLASS_ICON_PATH = "/nodes/class.png";
  public static final Icon CLASS_ICON = IconLoader.getIcon(CLASS_ICON_PATH);
  public static final Icon EXCEPTION_CLASS_ICON = IconLoader.getIcon("/nodes/exceptionClass.png");
  public static final Icon ANONYMOUS_CLASS_ICON = IconLoader.getIcon("/nodes/anonymousClass.png");
  public static final Icon ABSTRACT_CLASS_ICON = IconLoader.getIcon("/nodes/abstractClass.png");
  public static final Icon JUNIT_TEST_CLASS_ICON = IconLoader.getIcon("/nodes/junitTestClass.png");
  public static final Icon ANNOTATION_TYPE_ICON = IconLoader.getIcon("/nodes/annotationtype.png");
  public static final Icon ENUM_ICON = IconLoader.getIcon("/nodes/enum.png");
  public static final Icon INTERFACE_ICON = IconLoader.getIcon("/nodes/interface.png");
  public static final Icon METHOD_ICON = IconLoader.getIcon("/nodes/method.png");
  public static final Icon FUNCTION_ICON = IconLoader.getIcon("/nodes/function.png");
  public static final Icon ABSTRACT_METHOD_ICON = IconLoader.getIcon("/nodes/abstractMethod.png");
  public static final Icon FIELD_ICON = IconLoader.getIcon("/nodes/field.png");
  public static final Icon PARAMETER_ICON = IconLoader.getIcon("/nodes/parameter.png");
  public static final Icon VARIABLE_ICON = IconLoader.getIcon("/nodes/variable.png");
  public static final Icon XML_TAG_ICON = IconLoader.getIcon("/nodes/tag.png");
  public static final Icon ANT_TARGET_ICON = IconLoader.getIcon("/ant/target.png");
  public static final Icon ANT_META_TARGET_ICON = IconLoader.getIcon("/ant/metaTarget.png");
  public static final Icon LIBRARY_ICON = IconLoader.getIcon("/nodes/ppLib.png");
  public static final Icon WEB_ICON = IconLoader.getIcon("/nodes/ppWeb.png");
  public static final Icon JAR_ICON = IconLoader.getIcon("/nodes/ppJar.png");
  public static final Icon WEB_FOLDER_OPEN = IconLoader.getIcon("/nodes/webFolderOpen.png");
  public static final Icon WEB_FOLDER_CLOSED = IconLoader.getIcon("/nodes/webFolderClosed.png");
  public static final Icon FILE_ICON = IconLoader.getIcon("/nodes/ppFile.png");
  public static final Icon EJB_CLASS_ICON = IconLoader.getIcon("/javaee/ejbClass.png");
  public static final Icon EJB_INTERCEPTOR_CLASS_ICON = IconLoader.getIcon("/javaee/interceptorClass.png");
  public static final Icon EJB_HOME_INTERFACE_ICON = IconLoader.getIcon("/javaee/home.png");
  public static final Icon EJB_LOCAL_HOME_INTERFACE_ICON = IconLoader.getIcon("/javaee/localHome.png");
  public static final Icon EJB_LOCAL_INTERFACE_ICON = IconLoader.getIcon("/javaee/local.png");
  public static final Icon EJB_REMOTE_INTERFACE_ICON = IconLoader.getIcon("/javaee/remote.png");
  public static final Icon EJB_ICON = IconLoader.getIcon("/nodes/ejb.png");
  public static final Icon EJB_CREATE_METHOD_ICON = IconLoader.getIcon("/nodes/ejbCreateMethod.png");
  public static final Icon EJB_FINDER_METHOD_ICON = IconLoader.getIcon("/nodes/ejbFinderMethod.png");
  public static final Icon EJB_BUSINESS_METHOD_ICON = IconLoader.getIcon("/nodes/ejbBusinessMethod.png");
  public static final Icon EJB_CMP_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmpField.png");
  public static final Icon EJB_CMR_FIELD_ICON = IconLoader.getIcon("/nodes/ejbCmrField.png");
  public static final Icon EJB_PRIMARY_KEY_CLASS = IconLoader.getIcon("/nodes/ejbPrimaryKeyClass.png");
  public static final Icon EJB_REFERENCE = IconLoader.getIcon("/nodes/ejbReference.png");

  public static final Icon DATASOURCE_ICON = IconLoader.getIcon("/nodes/DataSource.png");
  public static final Icon DATASOURCE_DISABLED_ICON = IconLoader.getIcon("/nodes/DataSourceDisabled.png");
  public static final Icon DATASOURCE_TABLE_ICON = IconLoader.getIcon("/nodes/DataTables.png");
  public static final Icon DATASOURCE_VIEW_ICON = IconLoader.getIcon("/nodes/dataView.png");
  public static final Icon DATASOURCE_SEQUENCE_ICON = IconLoader.getIcon("/nodes/dataSequence.png");
  public static final Icon DATASOURCE_COLUMN_ICON = IconLoader.getIcon("/nodes/dataColumn.png");
  public static final Icon DATASOURCE_FK_COLUMN_ICON = IconLoader.getIcon("/nodes/dataFkColumn.png");
  public static final Icon DATASOURCE_PK_COLUMN_ICON = IconLoader.getIcon("/nodes/dataPkColumn.png");

  public static final Icon DATASOURCE_REMOTE_INSTANCE = IconLoader.getIcon("/nodes/addRemoteWeblogicInstance.png");
  public static final Icon EJB_FIELD_PK = IconLoader.getIcon("/nodes/fieldPK.png");
  public static final Icon VARIABLE_READ_ACCESS = IconLoader.getIcon("/nodes/read-access.png");
  public static final Icon VARIABLE_WRITE_ACCESS = IconLoader.getIcon("/nodes/write-access.png");
  public static final Icon VARIABLE_RW_ACCESS = IconLoader.getIcon("/nodes/rw-access.png");
  public static final Icon CUSTOM_FILE_ICON = IconLoader.getIcon("/fileTypes/custom.png");
  public static final Icon PROPERTY_ICON = IconLoader.getIcon("/nodes/property.png");
  public static final Icon ASPECT_ICON = IconLoader.getIcon("/nodes/aspect.png");
  public static final Icon POINTCUT_ICON = IconLoader.getIcon("/nodes/pointcut.png");
  public static final Icon ADVICE_ICON = IconLoader.getIcon("/nodes/advice.png");
  public static final Icon ERROR_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/errorIntroduction.png");
  public static final Icon WARNING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/warningIntroduction.png");
  public static final Icon PARENTS_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/parentsIntroduction.png");
  public static final Icon SOFTENING_INTRODUCTION_ICON = IconLoader.getIcon("/nodes/softeningIntroduction.png");
  public static final Icon JAVA_OUTSIDE_SOURCE_ICON = IconLoader.getIcon("/fileTypes/javaOutsideSource.png");
  public static final Icon EXCLUDED_FROM_COMPILE_ICON = IconLoader.getIcon("/nodes/excludedFromCompile.png");
  public static final Icon PROJECT_ICON = IconLoader.getIcon("/icon_small.png");
  public static final Icon UI_FORM_ICON = IconLoader.getIcon("/fileTypes/uiForm.png");
  public static final Icon JSP_ICON = IconLoader.getIcon("/fileTypes/jsp.png");
  public static final Icon SMALL_VCS_CONFIGURABLE = IconLoader.getIcon("/general/smallConfigurableVcs.png");
  public static final Icon VCS_SMALL_TAB = IconLoader.getIcon("/general/vcsSmallTab.png");
  public static final Icon GROUP_BY_PACKAGES = IconLoader.getIcon("/toolbar/folders.png");
  public static final Icon ADD_ICON = IconLoader.getIcon("/actions/include.png");
  public static final Icon DELETE_ICON = IconLoader.getIcon("/actions/exclude.png");
  public static final Icon DUPLICATE_ICON = IconLoader.getIcon("/general/copy.png");
  public static final Icon SELECT_ALL_ICON = IconLoader.getIcon("/actions/selectall.png");
  public static final Icon UNSELECT_ALL_ICON = IconLoader.getIcon("/actions/unselectall.png");
  public static final Icon PROPERTIES_ICON = IconLoader.getIcon("/actions/properties.png");
  public static final Icon SYNCHRONIZE_ICON = IconLoader.getIcon("/actions/sync.png");

  public static final Icon OPEN_EDIT_DIALOG_ICON = IconLoader.getIcon("/actions/showViewer.png");
  public static final Icon FLATTEN_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/flattenPackages.png");
  public static final Icon ADD_TO_SECTION_ICON = IconLoader.getIcon("/general/addItemToSection.png");
  public static final Icon EDIT_IN_SECTION_ICON = IconLoader.getIcon("/general/editItemInSection.png");
  public static final Icon TASK_ICON = IconLoader.getIcon("/ant/task.png");
  public static final Icon CLASS_INITIALIZER = IconLoader.getIcon("/nodes/classInitializer.png");

  public static final Icon OPENED_MODULE_GROUP_ICON = IconLoader.getIcon("/nodes/moduleGroupOpen.png");
  public static final Icon CLOSED_MODULE_GROUP_ICON = IconLoader.getIcon("/nodes/moduleGroupClosed.png");
  public static final Icon FOLDER_ICON = IconLoader.getIcon("/nodes/folder.png");
  public static final Icon SOURCE_FOLDERS_ICON = IconLoader.getIcon("/nodes/sourceFolder.png");
  public static final Icon TEST_SOURCE_FOLDER = IconLoader.getIcon("/nodes/testSourceFolder.png");

  public static final Icon CONTENT_ROOT_ICON_OPEN = IconLoader.getIcon("/nodes/ModuleOpen.png");
  public static final Icon CONTENT_ROOT_ICON_CLOSED = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public static final Icon UP_DOWN_ARROWS = IconLoader.getIcon("/ide/upDown.png");
}

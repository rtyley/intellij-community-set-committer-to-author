/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.spellchecker.state;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.dictionary.EditableDictionary;
import com.intellij.spellchecker.dictionary.ProjectDictionary;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@State(
  name = "ProjectDictionaryState",
  storages = {@Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/dictionaries/",
    scheme = StorageScheme.DIRECTORY_BASED, stateSplitter = ProjectDictionarySplitter.class)})

public class ProjectDictionaryState implements PersistentStateComponent<ProjectDictionaryState>{

  @Property(surroundWithTag = false) @AbstractCollection(surroundWithTag = false, elementTypes = DictionaryState.class)
  public List<DictionaryState> dictionaryStates = new ArrayList<DictionaryState>();


  private ProjectDictionary projectDictionary;
  private String currentUser;
  private Project project;

  public ProjectDictionaryState() {
  }

  public void setProject(Project project) {
     this.project = project;
   }

   public void setCurrentUser(String currentUser) {
     this.currentUser = currentUser;
   }


  @Transient
  public void setProjectDictionary(ProjectDictionary projectDictionary) {
    currentUser = projectDictionary.getActiveName();
    dictionaryStates.clear();
    Set<EditableDictionary> projectDictionaries = projectDictionary.getDictionaries();
    if (projectDictionaries != null) {
      for (EditableDictionary dic : projectDictionary.getDictionaries()) {
        dictionaryStates.add(new DictionaryState(dic));
      }
    }
  }

  @Transient
  public ProjectDictionary getProjectDictionary() {
    if (projectDictionary==null){
      projectDictionary = new ProjectDictionary();
    }
    return projectDictionary;
  }

  public ProjectDictionaryState getState() {
    if (projectDictionary!=null){
      //ensure all dictionaries within project dictionary will be stored
      setProjectDictionary(projectDictionary);
    }
    return this;
  }
  

  public void loadState(ProjectDictionaryState state) {
    if (state != null) {
      this.dictionaryStates = state.dictionaryStates;
    }
    retrieveProjectDictionaries();
  }

  private void retrieveProjectDictionaries() {
    Set<EditableDictionary> dictionaries = new THashSet<EditableDictionary>();
    if (dictionaryStates != null) {
      for (DictionaryState dictionaryState : dictionaryStates) {
        dictionaryState.loadState(dictionaryState);
        dictionaries.add(dictionaryState.getDictionary());
      }
    }
    projectDictionary = new ProjectDictionary(dictionaries);
  }

  @Override
  public String toString() {
    return "ProjectDictionaryState{" + "projectDictionary=" + projectDictionary + '}';
  }
}

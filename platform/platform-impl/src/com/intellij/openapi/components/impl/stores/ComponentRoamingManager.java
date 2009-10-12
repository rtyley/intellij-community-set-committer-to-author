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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;

import java.util.HashMap;
import java.util.Map;

public class ComponentRoamingManager {

  private final static ComponentRoamingManager OUR_INSTANCE = new ComponentRoamingManager();

  private final Map<String, RoamingType> myRoamingTypeMap = new HashMap<String, RoamingType>();

  public static ComponentRoamingManager getInstance(){
    return OUR_INSTANCE;
  }

  public RoamingType getRoamingType(String name){
    if (myRoamingTypeMap.containsKey(name)) {
      return myRoamingTypeMap.get(name);
    }

    return RoamingType.PER_USER;
  }

  public void setRoamingType(final String name, final RoamingType roamingType) {
     myRoamingTypeMap.put(name, roamingType);
  }

  public boolean typeSpecified(final String name) {
    return myRoamingTypeMap.containsKey(name);
  }
}

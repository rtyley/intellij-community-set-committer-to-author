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
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Key;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StubTree {
  private static final Key<StubTree> STUB_TO_TREE_REFERENCE = Key.create("stub to tree reference");
  private final PsiFileStub myRoot;
  private final List<StubElement<?>> myPlainList = new ArrayList<StubElement<?>>();

  public StubTree(@NotNull final PsiFileStub root) {
    this(root, true);
  }

  public StubTree(@NotNull final PsiFileStub root, final boolean withBackReference) {
    myRoot = root;
    enumerateStubs(root, myPlainList);
    if (withBackReference) {
      myRoot.putUserData(STUB_TO_TREE_REFERENCE, this); // This will prevent soft references to stub tree to be collected before all of the stubs are collected.
    }
  }

  private static void enumerateStubs(final StubElement<?> root, final List<StubElement<?>> result) {
    ((StubBase)root).id = result.size();
    result.add(root);
    for (StubElement child : root.getChildrenStubs()) {
      enumerateStubs(child, result);
    }
  }

  @NotNull
  public PsiFileStub getRoot() {
    return myRoot;
  }

  public List<StubElement<?>> getPlainList() {
    return myPlainList;
  }

  @NotNull
  public Map<StubIndexKey, Map<Object, int[]>> indexStubTree() {
    StubIndexSink sink = new StubIndexSink();

    for (int i = 0, plainListSize = myPlainList.size(); i < plainListSize; i++) {
      final StubElement<?> stub = myPlainList.get(i);
      sink.myStubIdx = i;
      StubSerializationUtil.getSerializer(stub).indexStub(stub, sink);
    }

    return sink.getResult();
  }

  private static class StubIndexSink implements IndexSink, TObjectProcedure<Map<Object, int[]>>,TObjectObjectProcedure<Object,int[]> {
    private final THashMap<StubIndexKey, Map<Object, int[]>> myResult = new THashMap<StubIndexKey, Map<Object, int[]>>();;
    private int myStubIdx;
    private Map<Object, int[]> myProcessingMap;

    @Override
    public void occurrence(@NotNull final StubIndexKey indexKey, @NotNull final Object value) {
      Map<Object, int[]> map = myResult.get(indexKey);
      if (map == null) {
        map = new THashMap<Object, int[]>();
        myResult.put(indexKey, map);
      }

      int[] list = map.get(value);
      if (list == null) {
        map.put(value, new int[] {myStubIdx});
      } else {
        int lastZero;
        for(lastZero = list.length - 1; lastZero >=0 && list[lastZero] == 0; --lastZero);
        ++lastZero;

        if (lastZero == list.length) {
          int[] newlist = new int[Math.max(4, list.length << 1)];
          System.arraycopy(list, 0, newlist, 0, list.length);
          lastZero = list.length;
          map.put(value, list = newlist);
        }
        list[lastZero] = myStubIdx;
      }
    }

    public Map<StubIndexKey, Map<Object, int[]>> getResult() {
      myResult.forEachValue(this);
      return myResult;
    }

    @Override
    public boolean execute(Map<Object, int[]> object) {
      myProcessingMap = object;
      ((THashMap<Object, int[]>)object).forEachEntry(this);
      return true;
    }

    @Override
    public boolean execute(Object a, int[] b) {
      if (b.length == 1) return true;
      int firstZero;
      for(firstZero = 0; firstZero < b.length && b[firstZero] != 0; ++firstZero);
      if (firstZero != b.length) {
        int[] shorterList = new int[firstZero];
        System.arraycopy(b, 0, shorterList, 0, shorterList.length);
        myProcessingMap.put(a, shorterList);
      }
      return true;
    }
  }
}

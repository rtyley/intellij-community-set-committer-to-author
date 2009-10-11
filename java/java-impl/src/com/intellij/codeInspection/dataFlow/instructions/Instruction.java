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
 * Date: Jan 26, 2002
 * Time: 10:46:40 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public abstract class Instruction {
  private int myIndex;
  private final ArrayList<DfaMemoryState> myProcessedStates;

  protected Instruction() {
    myProcessedStates = new ArrayList<DfaMemoryState>();
  }

  protected final DfaInstructionState[] nextInstruction(DataFlowRunner runner, DfaMemoryState stateBefore) {
    return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getIndex() + 1), stateBefore)};
  }

  public abstract DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor);

  public boolean isMemoryStateProcessed(DfaMemoryState dfaMemState) {
    for (DfaMemoryState state : myProcessedStates) {
      if (dfaMemState.equals(state)) {
        return true;
      }
    }

    return false;
  }

  public boolean setMemoryStateProcessed(DfaMemoryState dfaMemState) {
    if (myProcessedStates.size() > DataFlowRunner.MAX_STATES_PER_BRANCH) return false;
    myProcessedStates.add(dfaMemState);
    return true;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  @NonNls
  public String toString() {
    return super.toString();
  }
}

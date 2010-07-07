/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding.nodes;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class MulOperationNode extends ZenCodingNode {
  private final ZenCodingNode myLeftOperand;
  private final int myRightOperand;

  public MulOperationNode(ZenCodingNode leftOperand, int rightOperand) {
    myLeftOperand = leftOperand;
    myRightOperand = rightOperand;
  }

  public ZenCodingNode getLeftOperand() {
    return myLeftOperand;
  }

  public int getRightOperand() {
    return myRightOperand;
  }

  @NotNull
  @Override
  public List<GenerationNode> expand(int numberInIteration) {
    List<GenerationNode> result = new ArrayList<GenerationNode>();
    for (int i = 0; i < myRightOperand; i++) {
      result.addAll(myLeftOperand.expand(i));
    }
    return result;
  }
}

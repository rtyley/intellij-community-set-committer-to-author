package com.intellij.xdebugger;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class XTestCompositeNode extends XTestContainer<XValue> implements XCompositeNode {
  @Override
  public void addChildren(@NotNull XValueChildrenList children, boolean last) {
    final List<XValue> list = new ArrayList<XValue>();
    for (int i = 0; i < children.size(); i++) {
      list.add(children.getValue(i));
    }
    addChildren(list, last);
  }
}

/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.ruleinstance;

import com.wrq.rearranger.entry.ClassContentsEntry;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.Rule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

/** Instance to pick up header text. */
public class HeaderTrailerRuleInstance implements RuleInstance {

  @NotNull private final Rule       myRule;
  @Nullable private      RangeEntry myEntry;

  public HeaderTrailerRuleInstance(@NotNull Rule rule) {
    myRule = rule;
  }

  public boolean hasMatches() {
    return false;  // comments are never emitted based on whether header text is emitted, so false is fine.
  }

  public List<RangeEntry> getMatches() {
    List<RangeEntry> list = null;
    if (myEntry != null) {
      list = new ArrayList<RangeEntry>();
      list.add(myEntry);
    }
    return list;
  }

  @NotNull
  public Rule getRule() {
    return myRule;
  }

  public void addEntry(@Nullable RangeEntry entry) {
    myEntry = entry; // a header only has one entry
  }

  public void emit(Emitter emitter) {
    if (myEntry != null) {
      myEntry.emit(emitter);
    }
  }

  public void rearrangeRuleItems(List<ClassContentsEntry> entries, RearrangerSettings settings) {
  }

  public void addRuleInstanceToPopupTree(DefaultMutableTreeNode node, RearrangerSettings settings) {
    // don't show header/trailer junk in popup tree
  }
}

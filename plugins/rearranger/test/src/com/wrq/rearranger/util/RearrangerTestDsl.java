package com.wrq.rearranger.util;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates constants used at the Rearranger DSL.
 * 
 * @author Denis Zhdanov
 * @since 5/17/12 12:57 PM
 */
public enum RearrangerTestDsl {
  
  NAME,
  MODIFIER,
  /** Field initializer type. */
  INITIALIZER,
  /** Method target type (e.g. constructor). */
  TARGET,
  SORT,
  COMMENT,
  
  // Attributes
  INVERT,
  CONDITION,
  ALL_SUBSEQUENT("allSubsequent"),
  SUBSEQUENT_RULES_TO_MATCH("subsequentRulesToMatch");

  @NotNull private final String myValue;
  
  RearrangerTestDsl() {
    myValue = toString().toLowerCase();
  }

  RearrangerTestDsl(@NotNull String value) {
    myValue = value;
  }

  /**
   * @return    string value used at the Rearranger DSL
   */
  @NotNull
  public String getValue() {
    return myValue;
  }
}

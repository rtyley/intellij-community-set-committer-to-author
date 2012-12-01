package com.intellij.lang.properties.psi.impl;

import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public class PropertyImplEscaper extends LiteralTextEscaper<PropertyImpl> {
  private int[] outSourceOffsets;

  public PropertyImplEscaper(PropertyImpl value) {
    super(value);
  }

  @Override
  public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length() + 1];
    boolean b = PropertyImpl.parseCharacters(subText, outChars, outSourceOffsets);
    if (b) {
      for (int i=0; i<outChars.length(); i++) {
        if (outChars.charAt(i) != subText.charAt(outSourceOffsets[i])) {
          if (subText.charAt(outSourceOffsets[i]) != '\\') {
            throw new IllegalStateException();
          }
        }
      }
    }
    return b;
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return !myHost.getText().contains("\\");
  }
}

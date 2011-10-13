package com.intellij.lang.properties.editor;

import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.Properties;

/**
 * @author Denis Zhdanov
 * @since 10/5/11 2:35 PM
 */
public class ResourceBundleUtil {

  private static final TIntHashSet SYMBOLS_TO_ESCAPE = new TIntHashSet(new int[]{'#', '!', '=', ':'});
  private static final char        ESCAPE_SYMBOL     = '\\';
  
  private ResourceBundleUtil() {
  }

  /**
   * Allows to map given 'raw' property value text to the 'user-friendly' text to show at the resource bundle editor.
   * <p/>
   * <b>Note:</b> please refer to {@link Properties#load(Reader)} contract for the property value escape rules.
   * 
   * @param text  'raw' property value text
   * @return      'user-friendly' text to show at the resource bundle editor
   */
  @NotNull
  public static String fromPropertyValueToValueEditor(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ESCAPE_SYMBOL && !escaped) {
        escaped = true;
        continue;
      }
      buffer.append(c);
      escaped = false;
    }
    return buffer.toString();
  }

  /**
   * Perform reverse operation to {@link #fromPropertyValueToValueEditor(String)}.
   * 
   * @param text  'user-friendly' text shown to the user at the resource bundle editor
   * @return      'raw' value to store at the *.properties file
   */
  @NotNull
  public static String fromValueEditorToPropertyValue(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      
      if ((i == 0 && (c == ' ' || c == '\t')) // Leading white space
          || c == '\n'                        // Multi-line value
          || c == ESCAPE_SYMBOL               // Escaped 'escape' symbol
          || SYMBOLS_TO_ESCAPE.contains(c))   // Special symbol
      {
        buffer.append(ESCAPE_SYMBOL);
      }
      buffer.append(c);
    }
    return buffer.toString();
  }
}

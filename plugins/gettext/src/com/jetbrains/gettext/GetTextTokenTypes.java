package com.jetbrains.gettext;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * @author Svetlana.Zemlyanskaya
 */
public interface GetTextTokenTypes {

  IElementType COMMENT_SYMBOLS = new GetTextElementType("COMMENT_SYMBOLS");

  IElementType BAD_CHARACTER = new GetTextElementType("BAD_CHARACTER");
  IElementType WHITE_SPACE = new GetTextElementType("WHITE_SPACE");

  IElementType COMMENT = new GetTextElementType("COMMENT"); //#
  IElementType EXTR_COMMENT = new GetTextElementType("EXTR_COMMENT"); //#.
  IElementType REFERENCE = new GetTextElementType("REFERENCE"); //#:
  IElementType FLAG_COMMENT = new GetTextElementType("FLAG_COMMENT"); //#,
  IElementType PREVIOUS_COMMENT = new GetTextElementType("PREVIOUS_TRANSLATE_COMMENT"); //#|

  IElementType RANGE_FLAG = new GetTextElementType("RANGE_FLAG");
  IElementType FUZZY_FLAG = new GetTextElementType("FUZZY_FLAG");
  IElementType FORMAT_FLAG = new GetTextElementType("FORMAT_FLAG");
  IElementType NO_FORMAT_FLAG = new GetTextElementType("NO_FORMAT_FLAG");
  IElementType FLAG_DELIVERY = new GetTextElementType("DELIVERY");

  IElementType MSG = new GetTextElementType("MSG_START");

  IElementType MSGCTXT = new GetTextElementType("MSGCTXT");
  IElementType MSGID = new GetTextElementType("MSGID");
  IElementType MSGID_PLURAL = new GetTextElementType("MSGID_PLURAL");
  IElementType MSGSTR = new GetTextElementType("MSGSTR");

  IElementType COLON = new GetTextElementType("COLON");
  IElementType DOTS = new GetTextElementType("DOTS");
  IElementType NUMBER = new GetTextElementType("NUMBER");
  IElementType LBRACE = new GetTextElementType("LBRACE");
  IElementType RBRACE = new GetTextElementType("RBRACE");

  IElementType STRING = new GetTextElementType("STRING");

  TokenSet SYSTEM_COMMENTS = TokenSet.create(EXTR_COMMENT, REFERENCE, FLAG_COMMENT, PREVIOUS_COMMENT, FLAG_DELIVERY, COMMENT_SYMBOLS);
  TokenSet COMMENTS = TokenSet.orSet(TokenSet.create(COMMENT), SYSTEM_COMMENTS);
  TokenSet STRING_LITERALS = TokenSet.create(STRING);
  TokenSet KEYWORDS = TokenSet.create(MSG, MSGCTXT, MSGID, MSGID_PLURAL, MSGSTR);
  TokenSet FLAGS = TokenSet.create(FUZZY_FLAG, FORMAT_FLAG, NO_FORMAT_FLAG, RANGE_FLAG);
  TokenSet BRACES = TokenSet.create(LBRACE, RBRACE);
}

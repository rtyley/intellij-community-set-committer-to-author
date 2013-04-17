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

/* The following code was generated by JFlex 1.4.1 on 12.02.08 15:44 */

/* It's an automatically generated code. Do not modify it. */
package com.intellij.codeInsight.template.impl;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;


/**
 * This class is a scanner generated by
 * <a href="http://www.jflex.de/">JFlex</a> 1.4.1
 * on 12.02.08 15:44 from the specification file
 * <tt>C:/JetBrains/IDEA/tools/lexer/_MacroLexer.flex</tt>
 */
class _MacroLexer implements FlexLexer {
  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int YYINITIAL = 0;

  /**
   * Translates characters to character classes
   */
  private static final String ZZ_CMAP_PACKED =
    "\11\2\1\3\1\6\1\0\1\3\1\6\16\2\4\0\1\3\1\0"+
    "\1\4\1\0\1\1\3\0\1\7\1\10\2\0\1\11\3\0\12\2"+
    "\3\0\1\12\3\0\32\1\1\0\1\5\2\0\1\1\1\0\32\1"+
    "\4\0\41\2\2\0\4\1\4\0\1\1\2\0\1\2\7\0\1\1"+
    "\4\0\1\1\5\0\27\1\1\0\37\1\1\0\u013f\1\31\0\162\1"+
    "\4\0\14\1\16\0\5\1\11\0\1\1\21\0\130\2\5\0\23\2"+
    "\12\0\1\1\13\0\1\1\1\0\3\1\1\0\1\1\1\0\24\1"+
    "\1\0\54\1\1\0\46\1\1\0\5\1\4\0\202\1\1\0\4\2"+
    "\3\0\105\1\1\0\46\1\2\0\2\1\6\0\20\1\41\0\46\1"+
    "\2\0\1\1\7\0\47\1\11\0\21\2\1\0\27\2\1\0\3\2"+
    "\1\0\1\2\1\0\2\2\1\0\1\2\13\0\33\1\5\0\3\1"+
    "\15\0\4\2\14\0\6\2\13\0\32\1\5\0\13\1\16\2\7\0"+
    "\12\2\4\0\2\1\1\2\143\1\1\0\1\1\10\2\1\0\6\2"+
    "\2\1\2\2\1\0\4\2\2\1\12\2\3\1\2\0\1\1\17\0"+
    "\1\2\1\1\1\2\36\1\33\2\2\0\3\1\60\0\46\1\13\2"+
    "\1\1\u014f\0\3\2\66\1\2\0\1\2\1\1\20\2\2\0\1\1"+
    "\4\2\3\0\12\1\2\2\2\0\12\2\21\0\3\2\1\0\10\1"+
    "\2\0\2\1\2\0\26\1\1\0\7\1\1\0\1\1\3\0\4\1"+
    "\2\0\1\2\1\1\7\2\2\0\2\2\2\0\3\2\11\0\1\2"+
    "\4\0\2\1\1\0\3\1\2\2\2\0\12\2\4\1\15\0\3\2"+
    "\1\0\6\1\4\0\2\1\2\0\26\1\1\0\7\1\1\0\2\1"+
    "\1\0\2\1\1\0\2\1\2\0\1\2\1\0\5\2\4\0\2\2"+
    "\2\0\3\2\13\0\4\1\1\0\1\1\7\0\14\2\3\1\14\0"+
    "\3\2\1\0\11\1\1\0\3\1\1\0\26\1\1\0\7\1\1\0"+
    "\2\1\1\0\5\1\2\0\1\2\1\1\10\2\1\0\3\2\1\0"+
    "\3\2\2\0\1\1\17\0\2\1\2\2\2\0\12\2\1\0\1\1"+
    "\17\0\3\2\1\0\10\1\2\0\2\1\2\0\26\1\1\0\7\1"+
    "\1\0\2\1\1\0\5\1\2\0\1\2\1\1\6\2\3\0\2\2"+
    "\2\0\3\2\10\0\2\2\4\0\2\1\1\0\3\1\4\0\12\2"+
    "\1\0\1\1\20\0\1\2\1\1\1\0\6\1\3\0\3\1\1\0"+
    "\4\1\3\0\2\1\1\0\1\1\1\0\2\1\3\0\2\1\3\0"+
    "\3\1\3\0\10\1\1\0\3\1\4\0\5\2\3\0\3\2\1\0"+
    "\4\2\11\0\1\2\17\0\11\2\11\0\1\1\7\0\3\2\1\0"+
    "\10\1\1\0\3\1\1\0\27\1\1\0\12\1\1\0\5\1\4\0"+
    "\7\2\1\0\3\2\1\0\4\2\7\0\2\2\11\0\2\1\4\0"+
    "\12\2\22\0\2\2\1\0\10\1\1\0\3\1\1\0\27\1\1\0"+
    "\12\1\1\0\5\1\2\0\1\2\1\1\7\2\1\0\3\2\1\0"+
    "\4\2\7\0\2\2\7\0\1\1\1\0\2\1\4\0\12\2\22\0"+
    "\2\2\1\0\10\1\1\0\3\1\1\0\27\1\1\0\20\1\4\0"+
    "\6\2\2\0\3\2\1\0\4\2\11\0\1\2\10\0\2\1\4\0"+
    "\12\2\22\0\2\2\1\0\22\1\3\0\30\1\1\0\11\1\1\0"+
    "\1\1\2\0\7\1\3\0\1\2\4\0\6\2\1\0\1\2\1\0"+
    "\10\2\22\0\2\2\15\0\60\1\1\2\2\1\7\2\4\0\10\1"+
    "\10\2\1\0\12\2\47\0\2\1\1\0\1\1\2\0\2\1\1\0"+
    "\1\1\2\0\1\1\6\0\4\1\1\0\7\1\1\0\3\1\1\0"+
    "\1\1\1\0\1\1\2\0\2\1\1\0\4\1\1\2\2\1\6\2"+
    "\1\0\2\2\1\1\2\0\5\1\1\0\1\1\1\0\6\2\2\0"+
    "\12\2\2\0\2\1\42\0\1\1\27\0\2\2\6\0\12\2\13\0"+
    "\1\2\1\0\1\2\1\0\1\2\4\0\2\2\10\1\1\0\42\1"+
    "\6\0\24\2\1\0\2\2\4\1\4\0\10\2\1\0\44\2\11\0"+
    "\1\2\71\0\42\1\1\0\5\1\1\0\2\1\1\0\7\2\3\0"+
    "\4\2\6\0\12\2\6\0\6\1\4\2\106\0\46\1\12\0\51\1"+
    "\7\0\132\1\5\0\104\1\5\0\122\1\6\0\7\1\1\0\77\1"+
    "\1\0\1\1\1\0\4\1\2\0\7\1\1\0\1\1\1\0\4\1"+
    "\2\0\47\1\1\0\1\1\1\0\4\1\2\0\37\1\1\0\1\1"+
    "\1\0\4\1\2\0\7\1\1\0\1\1\1\0\4\1\2\0\7\1"+
    "\1\0\7\1\1\0\27\1\1\0\37\1\1\0\1\1\1\0\4\1"+
    "\2\0\7\1\1\0\47\1\1\0\23\1\16\0\11\2\56\0\125\1"+
    "\14\0\u026c\1\2\0\10\1\12\0\32\1\5\0\113\1\3\0\3\1"+
    "\17\0\15\1\1\0\4\1\3\2\13\0\22\1\3\2\13\0\22\1"+
    "\2\2\14\0\15\1\1\0\3\1\1\0\2\2\14\0\64\1\40\2"+
    "\3\0\1\1\3\0\2\1\1\2\2\0\12\2\41\0\3\2\2\0"+
    "\12\2\6\0\130\1\10\0\51\1\1\2\126\0\35\1\3\0\14\2"+
    "\4\0\14\2\12\0\12\2\36\1\2\0\5\1\u038b\0\154\1\224\0"+
    "\234\1\4\0\132\1\6\0\26\1\2\0\6\1\2\0\46\1\2\0"+
    "\6\1\2\0\10\1\1\0\1\1\1\0\1\1\1\0\1\1\1\0"+
    "\37\1\2\0\65\1\1\0\7\1\1\0\1\1\3\0\3\1\1\0"+
    "\7\1\3\0\4\1\2\0\6\1\4\0\15\1\5\0\3\1\1\0"+
    "\7\1\17\0\4\2\32\0\5\2\20\0\2\1\23\0\1\1\13\0"+
    "\4\2\6\0\6\2\1\0\1\1\15\0\1\1\40\0\22\1\36\0"+
    "\15\2\4\0\1\2\3\0\6\2\27\0\1\1\4\0\1\1\2\0"+
    "\12\1\1\0\1\1\3\0\5\1\6\0\1\1\1\0\1\1\1\0"+
    "\1\1\1\0\4\1\1\0\3\1\1\0\7\1\3\0\3\1\5\0"+
    "\5\1\26\0\44\1\u0e81\0\3\1\31\0\11\1\6\2\1\0\5\1"+
    "\2\0\5\1\4\0\126\1\2\0\2\2\2\0\3\1\1\0\137\1"+
    "\5\0\50\1\4\0\136\1\21\0\30\1\70\0\20\1\u0200\0\u19b6\1"+
    "\112\0\u51a6\1\132\0\u048d\1\u0773\0\u2ba4\1\u215c\0\u012e\1\2\0\73\1"+
    "\225\0\7\1\14\0\5\1\5\0\1\1\1\2\12\1\1\0\15\1"+
    "\1\0\5\1\1\0\1\1\1\0\2\1\1\0\2\1\1\0\154\1"+
    "\41\0\u016b\1\22\0\100\1\2\0\66\1\50\0\15\1\3\0\20\2"+
    "\20\0\4\2\17\0\2\1\30\0\3\1\31\0\1\1\6\0\5\1"+
    "\1\0\207\1\2\0\1\2\4\0\1\1\13\0\12\2\7\0\32\1"+
    "\4\0\1\1\1\0\32\1\12\0\132\1\3\0\6\1\2\0\6\1"+
    "\2\0\6\1\2\0\3\1\3\0\2\1\3\0\2\1\22\0\3\2"+
    "\4\0";

  /**
   * Translates characters to character classes
   */
  private static final char [] ZZ_CMAP = zzUnpackCMap(ZZ_CMAP_PACKED);

  /**
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\1\0\1\1\1\2\1\3\1\4\1\5\1\6\1\7"+
    "\1\10\2\4";

  private static int [] zzUnpackAction() {
    int [] result = new int[11];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /**
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\13\0\26\0\41\0\54\0\13\0\13\0\13"+
    "\0\13\0\13\0\67";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[11];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /**
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpackTrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\1\2\1\3\1\2\1\4\1\5\1\2\1\4\1\6"+
    "\1\7\1\10\1\11\14\0\2\3\13\0\1\4\2\0"+
    "\1\4\4\0\4\5\1\12\1\13\1\0\12\5\1\0"+
    "\4\5";

  private static int [] zzUnpackTrans() {
    int [] result = new int[66];
    int offset = 0;
    offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackTrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;
  private static final char[] EMPTY_BUFFER = new char[0];
  private static final int YYEOF = -1;
  private static java.io.Reader zzReader = null; // Fake

  /* error messages for the codes above */
  private static final String ZZ_ERROR_MSG[] = {
    "Unkown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state <code>aState</code>
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\1\0\1\11\3\1\5\11\1\1";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[11];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private CharSequence zzBuffer = "";

  /** this buffer may contains the current text array to be matched when it is cheap to acquire it */
  private char[] zzBufferArray;

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the textposition at the last state to be included in yytext */
  private int zzPushbackPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /**
   * zzAtBOL == true <=> the scanner is currently at the beginning of a line
   */
  private boolean zzAtBOL = true;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** denotes if the user-EOF-code has already been executed */
  private boolean zzEOFDone;

  /* user code: */
   public _MacroLexer() {
     this((java.io.Reader)null);
   }


  _MacroLexer(java.io.Reader in) {
    this.zzReader = in;
  }

  /**
   * Creates a new scanner.
   * There is also java.io.Reader version of this constructor.
   *
   * @param   in  the java.io.Inputstream to read input from.
   */
  _MacroLexer(java.io.InputStream in) {
    this(new java.io.InputStreamReader(in));
  }

  /**
   * Unpacks the compressed character translation table.
   *
   * @param packed   the packed character translation table
   * @return         the unpacked character translation table
   */
  private static char [] zzUnpackCMap(String packed) {
    char [] map = new char[0x10000];
    int i = 0;  /* index in packed string  */
    int j = 0;  /* index in unpacked array */
    while (i < 1682) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }

  @Override
  public final int getTokenStart(){
    return zzStartRead;
  }

  @Override
  public final int getTokenEnd(){
    return getTokenStart() + yylength();
  }

  @Override
  public void reset(CharSequence buffer, int start, int end,int initialState){
    zzBuffer = buffer;
    zzBufferArray = com.intellij.util.text.CharArrayUtil.fromSequenceWithoutCopying(buffer);
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzPushbackPos = 0;
    zzAtEOF  = false;
    zzAtBOL = true;
    zzEndRead = end;
    yybegin(initialState);
  }

  /**
   * Refills the input buffer.
   *
   * @return      <code>false</code>, iff there was new input.
   *
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {
    return true;
  }


  /**
   * Returns the current lexical state.
   */
  @Override
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  @Override
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }


  /**
   * Returns the character at position <tt>pos</tt> from the
   * matched text.
   *
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch.
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBufferArray != null ? zzBufferArray[zzStartRead+pos]:zzBuffer.charAt(zzStartRead+pos);
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occured while scanning.
   *
   * In a wellformed scanner (no or only correct usage of
   * yypushback(int) and a match-all fallback rule) this method
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  }


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Contains user EOF-code, which will be executed exactly once,
   * when the end of file is reached
   */
  private void zzDoEOF() {
    if (!zzEOFDone) {
      zzEOFDone = true;

    }
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  @Override
  public IElementType advance() throws java.io.IOException {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    CharSequence zzBufferL = zzBuffer;
    char[] zzBufferArrayL = zzBufferArray;
    char [] zzCMapL = ZZ_CMAP;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

      zzState = zzLexicalState;


      zzForAction: {
        while (true) {

          if (zzCurrentPosL < zzEndReadL)
            zzInput = zzBufferArrayL != null ? zzBufferArrayL[zzCurrentPosL++]:zzBufferL.charAt(zzCurrentPosL++);
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = zzBufferArrayL != null ? zzBufferArrayL[zzCurrentPosL++]:zzBufferL.charAt(zzCurrentPosL++);
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + zzCMapL[zzInput] ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          int zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
        case 6:
          { return MacroTokenType.RPAREN;
          }
        case 9: break;
        case 4:
          { return MacroTokenType.STRING_LITERAL;
          }
        case 10: break;
        case 2:
          { return MacroTokenType.IDENTIFIER;
          }
        case 11: break;
        case 1:
          { return TokenType.BAD_CHARACTER;
          }
        case 12: break;
        case 5:
          { return MacroTokenType.LPAREN;
          }
        case 13: break;
        case 8:
          { return MacroTokenType.EQ;
          }
        case 14: break;
        case 7:
          { return MacroTokenType.COMMA;
          }
        case 15: break;
        case 3:
          { return MacroTokenType.WHITE_SPACE;
          }
        case 16: break;
        default:
          if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
            zzAtEOF = true;
            zzDoEOF();
            return null;
          }
          else {
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}

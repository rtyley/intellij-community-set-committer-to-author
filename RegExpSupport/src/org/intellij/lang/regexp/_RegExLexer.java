/* The following code was generated by JFlex 1.4.3 on 21.02.10 20:59 */

/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.regexp;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.LinkedList;
import com.intellij.psi.StringEscapesTokenTypes;

// IDEADEV-11055
@SuppressWarnings({ "ALL", "SameParameterValue", "WeakerAccess", "SameReturnValue", "RedundantThrows", "UnusedDeclaration", "UnusedDeclaration" })

/**
 * This class is a scanner generated by 
 * <a href="http://www.jflex.de/">JFlex</a> 1.4.3
 * on 21.02.10 20:59 from the specification file
 * <tt>C:/JetBrains/IDEA/tools/lexer/../../community/RegExpSupport/src/org/intellij/lang/regexp/regexp-lexer.flex</tt>
 */
class _RegExLexer implements FlexLexer {
  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int CLASS1 = 6;
  public static final int PROP = 10;
  public static final int EMBRACED = 4;
  public static final int QUOTED = 2;
  public static final int YYINITIAL = 0;
  public static final int COMMENT = 14;
  public static final int OPTIONS = 12;
  public static final int CLASS2 = 8;

  /**
   * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
   * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
   *                  at the beginning of a line
   * l is of the form l = 2*k, k a non negative integer
   */
  private static final int ZZ_LEXSTATE[] = { 
     0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,  7, 7
  };

  /** 
   * Translates characters to character classes
   */
  private static final String ZZ_CMAP_PACKED = 
    "\10\0\2\52\1\13\1\0\1\52\1\61\22\0\1\45\1\56\1\0"+
    "\1\60\1\15\1\0\1\51\1\0\1\4\1\5\1\17\1\20\1\50"+
    "\1\43\1\3\1\0\1\2\7\42\2\1\1\53\1\0\1\57\1\55"+
    "\1\54\1\16\1\0\1\24\1\24\1\30\1\27\1\37\1\35\1\25"+
    "\1\44\1\32\6\44\1\34\1\36\1\44\1\26\3\44\1\26\1\26"+
    "\1\44\1\25\1\10\1\12\1\11\1\14\1\46\1\0\1\23\1\24"+
    "\1\31\1\27\1\23\1\23\2\44\1\32\4\44\1\22\1\44\1\33"+
    "\1\44\1\22\1\26\1\22\1\41\1\44\1\26\1\40\1\44\1\25"+
    "\1\6\1\21\1\7\54\0\1\44\12\0\1\44\4\0\1\44\5\0"+
    "\27\44\1\0\37\44\1\0\u013f\44\31\0\162\44\4\0\14\44\16\0"+
    "\5\44\11\0\1\44\213\0\1\44\13\0\1\44\1\0\3\44\1\0"+
    "\1\44\1\0\24\44\1\0\54\44\1\0\46\44\1\0\5\44\4\0"+
    "\202\44\10\0\105\44\1\0\46\44\2\0\2\44\6\0\20\44\41\0"+
    "\46\44\2\0\1\44\7\0\47\44\110\0\33\44\5\0\3\44\56\0"+
    "\32\44\5\0\13\44\25\0\12\47\4\0\2\44\1\0\143\44\1\0"+
    "\1\44\17\0\2\44\7\0\2\44\12\47\3\44\2\0\1\44\20\0"+
    "\1\44\1\0\36\44\35\0\3\44\60\0\46\44\13\0\1\44\u0152\0"+
    "\66\44\3\0\1\44\22\0\1\44\7\0\12\44\4\0\12\47\25\0"+
    "\10\44\2\0\2\44\2\0\26\44\1\0\7\44\1\0\1\44\3\0"+
    "\4\44\3\0\1\44\36\0\2\44\1\0\3\44\4\0\12\47\2\44"+
    "\23\0\6\44\4\0\2\44\2\0\26\44\1\0\7\44\1\0\2\44"+
    "\1\0\2\44\1\0\2\44\37\0\4\44\1\0\1\44\7\0\12\47"+
    "\2\0\3\44\20\0\11\44\1\0\3\44\1\0\26\44\1\0\7\44"+
    "\1\0\2\44\1\0\5\44\3\0\1\44\22\0\1\44\17\0\2\44"+
    "\4\0\12\47\25\0\10\44\2\0\2\44\2\0\26\44\1\0\7\44"+
    "\1\0\2\44\1\0\5\44\3\0\1\44\36\0\2\44\1\0\3\44"+
    "\4\0\12\47\1\0\1\44\21\0\1\44\1\0\6\44\3\0\3\44"+
    "\1\0\4\44\3\0\2\44\1\0\1\44\1\0\2\44\3\0\2\44"+
    "\3\0\3\44\3\0\10\44\1\0\3\44\55\0\11\47\25\0\10\44"+
    "\1\0\3\44\1\0\27\44\1\0\12\44\1\0\5\44\46\0\2\44"+
    "\4\0\12\47\25\0\10\44\1\0\3\44\1\0\27\44\1\0\12\44"+
    "\1\0\5\44\3\0\1\44\40\0\1\44\1\0\2\44\4\0\12\47"+
    "\25\0\10\44\1\0\3\44\1\0\27\44\1\0\20\44\46\0\2\44"+
    "\4\0\12\47\25\0\22\44\3\0\30\44\1\0\11\44\1\0\1\44"+
    "\2\0\7\44\72\0\60\44\1\0\2\44\14\0\7\44\11\0\12\47"+
    "\47\0\2\44\1\0\1\44\2\0\2\44\1\0\1\44\2\0\1\44"+
    "\6\0\4\44\1\0\7\44\1\0\3\44\1\0\1\44\1\0\1\44"+
    "\2\0\2\44\1\0\4\44\1\0\2\44\11\0\1\44\2\0\5\44"+
    "\1\0\1\44\11\0\12\47\2\0\2\44\42\0\1\44\37\0\12\47"+
    "\26\0\10\44\1\0\42\44\35\0\4\44\164\0\42\44\1\0\5\44"+
    "\1\0\2\44\25\0\12\47\6\0\6\44\112\0\46\44\12\0\51\44"+
    "\7\0\132\44\5\0\104\44\5\0\122\44\6\0\7\44\1\0\77\44"+
    "\1\0\1\44\1\0\4\44\2\0\7\44\1\0\1\44\1\0\4\44"+
    "\2\0\47\44\1\0\1\44\1\0\4\44\2\0\37\44\1\0\1\44"+
    "\1\0\4\44\2\0\7\44\1\0\1\44\1\0\4\44\2\0\7\44"+
    "\1\0\7\44\1\0\27\44\1\0\37\44\1\0\1\44\1\0\4\44"+
    "\2\0\7\44\1\0\47\44\1\0\23\44\16\0\11\47\56\0\125\44"+
    "\14\0\u026c\44\2\0\10\44\12\0\32\44\5\0\113\44\25\0\15\44"+
    "\1\0\4\44\16\0\22\44\16\0\22\44\16\0\15\44\1\0\3\44"+
    "\17\0\64\44\43\0\1\44\4\0\1\44\3\0\12\47\46\0\12\47"+
    "\6\0\130\44\10\0\51\44\127\0\35\44\51\0\12\47\36\44\2\0"+
    "\5\44\u038b\0\154\44\224\0\234\44\4\0\132\44\6\0\26\44\2\0"+
    "\6\44\2\0\46\44\2\0\6\44\2\0\10\44\1\0\1\44\1\0"+
    "\1\44\1\0\1\44\1\0\37\44\2\0\65\44\1\0\7\44\1\0"+
    "\1\44\3\0\3\44\1\0\7\44\3\0\4\44\2\0\6\44\4\0"+
    "\15\44\5\0\3\44\1\0\7\44\164\0\1\44\15\0\1\44\202\0"+
    "\1\44\4\0\1\44\2\0\12\44\1\0\1\44\3\0\5\44\6\0"+
    "\1\44\1\0\1\44\1\0\1\44\1\0\4\44\1\0\3\44\1\0"+
    "\7\44\3\0\3\44\5\0\5\44\u0ebb\0\2\44\52\0\5\44\5\0"+
    "\2\44\4\0\126\44\6\0\3\44\1\0\132\44\1\0\4\44\5\0"+
    "\50\44\4\0\136\44\21\0\30\44\70\0\20\44\u0200\0\u19b6\44\112\0"+
    "\u51a6\44\132\0\u048d\44\u0773\0\u2ba4\44\u215c\0\u012e\44\2\0\73\44\225\0"+
    "\7\44\14\0\5\44\5\0\1\44\1\0\12\44\1\0\15\44\1\0"+
    "\5\44\1\0\1\44\1\0\2\44\1\0\2\44\1\0\154\44\41\0"+
    "\u016b\44\22\0\100\44\2\0\66\44\50\0\14\44\164\0\5\44\1\0"+
    "\207\44\23\0\12\47\7\0\32\44\6\0\32\44\13\0\131\44\3\0"+
    "\6\44\2\0\6\44\2\0\6\44\2\0\3\44\43\0";

  /** 
   * Translates characters to character classes
   */
  private static final char [] ZZ_CMAP = zzUnpackCMap(ZZ_CMAP_PACKED);

  /** 
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\6\0\1\1\1\2\1\3\1\4\1\5\1\6\1\7"+
    "\1\10\1\11\1\12\1\13\1\14\1\15\1\16\1\17"+
    "\1\20\1\21\1\22\1\23\1\3\1\24\1\25\1\26"+
    "\1\27\1\30\1\31\1\32\1\33\1\34\1\3\1\35"+
    "\1\36\1\35\1\37\1\40\1\1\1\41\1\42\1\2"+
    "\1\43\1\44\1\45\1\46\1\47\1\50\1\51\1\52"+
    "\1\53\1\54\2\55\1\56\1\11\1\57\1\60\1\61"+
    "\1\62\1\63\1\0\1\64\1\65\1\66\2\0\1\67"+
    "\1\70\2\60\2\61\1\0\1\71\1\72\1\0\1\67"+
    "\1\60\1\73\2\61\1\74\1\75\1\67\3\61\1\76";

  private static int [] zzUnpackAction() {
    int [] result = new int[92];
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
    "\0\0\0\62\0\144\0\226\0\310\0\372\0\u012c\0\u015e"+
    "\0\u0190\0\u0190\0\u01c2\0\u0190\0\u0190\0\u01f4\0\u0226\0\u0190"+
    "\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190"+
    "\0\u0190\0\u0258\0\u0190\0\u028a\0\u0190\0\u02bc\0\u0190\0\u0190"+
    "\0\u0190\0\u0190\0\u0190\0\u02ee\0\u0190\0\u0190\0\u01f4\0\u0190"+
    "\0\u0190\0\u0320\0\u0352\0\u0190\0\u0190\0\u0384\0\u0190\0\u0190"+
    "\0\u03b6\0\u03e8\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190\0\u0190"+
    "\0\u041a\0\u0190\0\u0190\0\u0190\0\u044c\0\u047e\0\u0190\0\u0190"+
    "\0\u04b0\0\u0190\0\u0190\0\u0190\0\u04e2\0\u0514\0\u0546\0\u0190"+
    "\0\u0578\0\u05aa\0\u05dc\0\u060e\0\u0640\0\u0190\0\u0190\0\u0672"+
    "\0\u06a4\0\u0190\0\u0190\0\u06d6\0\u0708\0\u0190\0\u0190\0\u0190"+
    "\0\u073a\0\u076c\0\u0190\0\u0190";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[92];
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
    "\3\11\1\12\1\13\1\14\1\15\1\11\1\16\1\11"+
    "\1\17\1\20\1\21\1\22\1\23\1\24\1\25\1\26"+
    "\21\11\1\27\1\11\1\30\4\11\1\20\5\11\1\31"+
    "\1\20\12\11\1\32\1\0\46\11\1\33\2\34\4\33"+
    "\1\35\12\33\20\36\1\34\1\33\1\36\2\33\1\34"+
    "\1\37\11\33\11\40\1\41\1\40\1\0\46\40\6\11"+
    "\1\15\1\11\1\16\1\42\1\17\1\43\1\21\26\11"+
    "\1\27\5\11\1\44\1\43\6\11\1\43\6\45\1\46"+
    "\1\45\1\47\1\45\1\17\47\45\5\50\1\51\14\50"+
    "\20\52\1\50\1\53\1\52\6\50\1\54\6\50\13\10"+
    "\1\55\45\10\1\55\100\0\1\56\54\0\1\57\50\0"+
    "\1\60\1\61\1\62\4\63\1\60\1\63\1\60\1\63"+
    "\1\64\6\63\2\65\2\66\2\67\1\70\1\71\1\70"+
    "\2\72\1\73\1\74\1\73\1\75\1\76\1\61\1\63"+
    "\1\73\1\64\4\60\1\64\6\60\1\64\37\0\1\77"+
    "\23\0\2\34\37\0\1\34\4\0\1\34\13\0\2\36"+
    "\17\0\21\36\1\0\1\36\1\0\2\36\63\0\1\100"+
    "\32\0\20\52\2\0\1\52\37\0\20\53\2\0\1\53"+
    "\51\0\1\101\16\0\2\102\1\103\1\104\1\105\1\106"+
    "\2\0\2\61\37\0\1\61\21\0\1\107\37\0\1\107"+
    "\17\0\62\110\1\111\2\112\20\111\2\112\2\111\3\112"+
    "\3\111\1\112\1\111\1\112\2\111\1\112\17\111\1\113"+
    "\2\114\20\113\2\114\2\113\3\114\3\113\1\114\1\113"+
    "\1\114\2\113\1\114\17\113\57\0\1\115\57\0\1\116"+
    "\1\117\3\0\5\120\1\0\54\120\2\0\1\121\37\0"+
    "\1\121\17\0\63\122\2\123\20\122\2\123\2\122\3\123"+
    "\3\122\1\123\1\122\1\123\2\122\1\123\17\122\63\124"+
    "\2\125\20\124\2\125\2\124\3\125\3\124\1\125\1\124"+
    "\1\125\2\124\1\125\17\124\22\0\20\115\2\0\1\115"+
    "\7\0\1\126\5\0\5\120\1\127\54\120\2\0\1\130"+
    "\37\0\1\130\17\0\63\131\2\132\20\131\2\132\2\131"+
    "\3\132\3\131\1\132\1\131\1\132\2\131\1\132\17\131"+
    "\63\133\2\134\20\133\2\134\2\133\3\134\3\133\1\134"+
    "\1\133\1\134\2\133\1\134\17\133";

  private static int [] zzUnpackTrans() {
    int [] result = new int[1950];
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
    "\6\0\2\1\2\11\1\1\2\11\2\1\12\11\1\1"+
    "\1\11\1\1\1\11\1\1\5\11\1\1\2\11\1\1"+
    "\2\11\2\1\2\11\1\1\2\11\2\1\6\11\1\1"+
    "\3\11\2\1\2\11\1\0\3\11\2\0\1\1\1\11"+
    "\4\1\1\0\2\11\1\0\1\1\2\11\2\1\3\11"+
    "\2\1\2\11";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[92];
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
    // This adds support for nested states. I'm no JFlex pro, so maybe this is overkill, but it works quite well.
    private final LinkedList<Integer> states = new LinkedList();

    // This was an idea to use the regex implementation for XML schema regexes (which use a slightly different syntax)
    // as well, but is currently unfinished as it requires to tweak more places than just the lexer. 
    private boolean xmlSchemaMode;

    private boolean allowDanglingMetacharacters;

    _RegExLexer(boolean xmlSchemaMode, boolean allowDanglingMetacharacters) {
      this((java.io.Reader)null);
      this.xmlSchemaMode = xmlSchemaMode;
      this.allowDanglingMetacharacters = allowDanglingMetacharacters;
    }

    private void yypushstate(int state) {
        states.addFirst(yystate());
        yybegin(state);
    }
    private void yypopstate() {
        final int state = states.removeFirst();
        yybegin(state);
    }

    private void handleOptions() {
      final String o = yytext().toString();
      if (o.contains("x")) {
        commentMode = !o.startsWith("-");
      }
    }

    // tracks whether the lexer is in comment mode, i.e. whether whitespace is not significant and whether to ignore
    // text after '#' till EOL
    boolean commentMode = false;


  _RegExLexer(java.io.Reader in) {
    this.zzReader = in;
  }

  /**
   * Creates a new scanner.
   * There is also java.io.Reader version of this constructor.
   *
   * @param   in  the java.io.Inputstream to read input from.
   */
  _RegExLexer(java.io.InputStream in) {
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
    while (i < 1336) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }

  public final int getTokenStart(){
    return zzStartRead;
  }

  public final int getTokenEnd(){
    return getTokenStart() + yylength();
  }

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

  // For Demetra compatibility
  public void reset(CharSequence buffer, int initialState){
    zzBuffer = buffer;
    zzBufferArray = null; 
    zzCurrentPos = zzMarkedPos = zzStartRead = 0;
    zzPushbackPos = 0;
    zzAtEOF = false;
    zzAtBOL = true;
    zzEndRead = buffer.length();
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
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
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

      zzState = ZZ_LEXSTATE[zzLexicalState];


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
        case 7: 
          { if (yystate() != CLASS2) yypushstate(EMBRACED); return RegExpTT.LBRACE;
          }
        case 63: break;
        case 6: 
          { return RegExpTT.GROUP_END;
          }
        case 64: break;
        case 36: 
          // lookahead expression with fixed base length
          zzMarkedPos = zzStartRead + 1;
          { yypushstate(CLASS1); return RegExpTT.CLASS_BEGIN;
          }
        case 65: break;
        case 14: 
          { return RegExpTT.STAR;
          }
        case 66: break;
        case 40: 
          { return RegExpTT.ESC_CHARACTER;
          }
        case 67: break;
        case 28: 
          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.ESC_CHARACTER;
          }
        case 68: break;
        case 41: 
          { return commentMode ? RegExpTT.CHARACTER : RegExpTT.REDUNDANT_ESCAPE;
          }
        case 69: break;
        case 37: 
          { return RegExpTT.REDUNDANT_ESCAPE;
          }
        case 70: break;
        case 24: 
          { return RegExpTT.COMMA;
          }
        case 71: break;
        case 57: 
          { return RegExpTT.POS_LOOKBEHIND;
          }
        case 72: break;
        case 39: 
          { return RegExpTT.BAD_OCT_VALUE;
          }
        case 73: break;
        case 10: 
          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CTRL_CHARACTER;
          }
        case 74: break;
        case 30: 
          { yypopstate(); yypushstate(EMBRACED); return RegExpTT.LBRACE;
          }
        case 75: break;
        case 49: 
          { return StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN;
          }
        case 76: break;
        case 58: 
          { return RegExpTT.NEG_LOOKBEHIND;
          }
        case 77: break;
        case 62: 
          { return RegExpTT.UNICODE_CHAR;
          }
        case 78: break;
        case 56: 
          { if (xmlSchemaMode) { yypushback(1); return RegExpTT.CHAR_CLASS; } else return RegExpTT.CTRL;
          }
        case 79: break;
        case 35: 
          { yybegin(OPTIONS); return RegExpTT.SET_OPTIONS;
          }
        case 80: break;
        case 12: 
          { return RegExpTT.DOLLAR;
          }
        case 81: break;
        case 50: 
          { yypopstate(); return RegExpTT.QUOTE_END;
          }
        case 82: break;
        case 53: 
          { return RegExpTT.POS_LOOKAHEAD;
          }
        case 83: break;
        case 22: 
          { yypopstate(); return RegExpTT.RBRACE;
          }
        case 84: break;
        case 3: 
          { return RegExpTT.CHARACTER;
          }
        case 85: break;
        case 54: 
          { return RegExpTT.NEG_LOOKAHEAD;
          }
        case 86: break;
        case 42: 
          { return RegExpTT.ESC_CTRL_CHARACTER;
          }
        case 87: break;
        case 23: 
          { return RegExpTT.NAME;
          }
        case 88: break;
        case 51: 
          { return RegExpTT.ANDAND;
          }
        case 89: break;
        case 13: 
          { return RegExpTT.QUEST;
          }
        case 90: break;
        case 44: 
          { return RegExpTT.CHAR_CLASS;
          }
        case 91: break;
        case 17: 
          { return RegExpTT.MINUS;
          }
        case 92: break;
        case 61: 
          { return RegExpTT.COMMENT;
          }
        case 93: break;
        case 38: 
          { return yystate() != CLASS2 ? RegExpTT.BACKREF : RegExpTT.ESC_CHARACTER;
          }
        case 94: break;
        case 16: 
          { return RegExpTT.UNION;
          }
        case 95: break;
        case 60: 
          { return RegExpTT.PYTHON_NAMED_GROUP;
          }
        case 96: break;
        case 45: 
          { if (xmlSchemaMode) return RegExpTT.CHAR_CLASS; else return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
          }
        case 97: break;
        case 55: 
          { return RegExpTT.OCT_CHAR;
          }
        case 98: break;
        case 4: 
          { return RegExpTT.DOT;
          }
        case 99: break;
        case 32: 
          { yybegin(YYINITIAL); return RegExpTT.GROUP_END;
          }
        case 100: break;
        case 21: 
          { return RegExpTT.NUMBER;
          }
        case 101: break;
        case 46: 
          { yypushstate(PROP); return RegExpTT.PROPERTY;
          }
        case 102: break;
        case 18: 
          { return commentMode ? com.intellij.psi.TokenType.WHITE_SPACE : RegExpTT.CHARACTER;
          }
        case 103: break;
        case 33: 
          { handleOptions(); return RegExpTT.OPTIONS_OFF;
          }
        case 104: break;
        case 34: 
          { yybegin(YYINITIAL); return RegExpTT.COLON;
          }
        case 105: break;
        case 25: 
          { assert false : yytext();
          }
        case 106: break;
        case 27: 
          { yypopstate(); return RegExpTT.CLASS_END;
          }
        case 107: break;
        case 11: 
          { return RegExpTT.CARET;
          }
        case 108: break;
        case 1: 
          { handleOptions(); return RegExpTT.OPTIONS_ON;
          }
        case 109: break;
        case 29: 
          { yypopstate(); yypushback(1);
          }
        case 110: break;
        case 43: 
          { return yystate() != CLASS2 ? RegExpTT.BOUNDARY : RegExpTT.ESC_CHARACTER;
          }
        case 111: break;
        case 19: 
          { if (commentMode) { yypushstate(COMMENT); return RegExpTT.COMMENT; } else return RegExpTT.CHARACTER;
          }
        case 112: break;
        case 59: 
          { return RegExpTT.HEX_CHAR;
          }
        case 113: break;
        case 5: 
          { return RegExpTT.GROUP_BEGIN;
          }
        case 114: break;
        case 8: 
          { yypushstate(CLASS2); return RegExpTT.CLASS_BEGIN;
          }
        case 115: break;
        case 52: 
          { return RegExpTT.NON_CAPT_GROUP;
          }
        case 116: break;
        case 9: 
          { return StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN;
          }
        case 117: break;
        case 48: 
          { return RegExpTT.BAD_HEX_VALUE;
          }
        case 118: break;
        case 2: 
          { yypopstate(); return RegExpTT.COMMENT;
          }
        case 119: break;
        case 47: 
          { yypushstate(QUOTED); return RegExpTT.QUOTE_BEGIN;
          }
        case 120: break;
        case 26: 
          { yybegin(CLASS2); return RegExpTT.CHARACTER;
          }
        case 121: break;
        case 15: 
          { return RegExpTT.PLUS;
          }
        case 122: break;
        case 20: 
          { if (allowDanglingMetacharacters) {
                          yypopstate(); yypushback(1); 
                        } else {
                          return RegExpTT.BAD_CHARACTER;
                        }
          }
        case 123: break;
        case 31: 
          { yybegin(YYINITIAL); return RegExpTT.BAD_CHARACTER;
          }
        case 124: break;
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

/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.xpath;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

@SuppressWarnings({"ALL"})
%%

%class _XPathLexer
%abstract
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

%{
  private boolean allowXPath2Syntax = false;

  _XPathLexer(boolean allowXPath2Syntax) {
    this((java.io.Reader)null);
    this.allowXPath2Syntax = allowXPath2Syntax;
  }

  public final void setStart(int start) {
    zzCurrentPos = zzStartRead = start;
  }

  private IElementType ncName() {
    yybegin(S1);
    return XPathTokenTypes.NCNAME;
  }

  protected abstract void readComment() throws java.io.IOException;
%}

WHITE_SPACE_CHAR=[\ \n\r\t]

NCNameChar=[:letter:] | [:digit:] | "." | "-" | "_"
NCName=([:letter:] | "_") {NCNameChar}*
QName=({NCName} ":")? {NCName}

STRING_LITERAL= "\"" ([^\"]|"\"\"")* "\""? | "'" ([^\']|"''")* "'"?
DIGITS=[:digit:]+
NUMBER= ({DIGITS} ("." {DIGITS}?)? | "." {DIGITS}) ([eE] [+-]? {DIGITS})?

AXIS= "ancestor"
    | "ancestor-or-self"
    | "attribute"
    | "child"
    | "descendant"
    | "descendant-or-self"
    | "following"
    | "following-sibling"
    | "namespace"
    | "parent"
    | "preceding"
    | "preceding-sibling"
    | "self"

NODE_TYPE= "comment"
    | "text"
    | "processing-instruction"
    | "node"

NODE_TYPE2 = "element" | "attribute" | "schema-element" | "document-node"

WS={WHITE_SPACE_CHAR}+

%state S1
%state FUNC
%state VAR

%%

{WS}                        { return XPathTokenTypes.WHITESPACE; }

"(:"                        { if (allowXPath2Syntax) { readComment(); return XPath2TokenTypes.COMMENT; } else { yypushback(1); return XPathTokenTypes.LPAREN; } }
":)"                        { if (allowXPath2Syntax) { return XPath2TokenTypes.END_COMMENT; } else { yypushback(1); return XPathTokenTypes.COL; } }

"$"                         { yybegin(VAR);         return XPathTokenTypes.DOLLAR; }
<VAR>
    {NCName} /
    {WS}? ":"               {                      return XPathTokenTypes.VARIABLE_PREFIX; }
<VAR> ":"                   {                      return XPathTokenTypes.COL; }
<VAR> {NCName}              { yybegin(S1);         return XPathTokenTypes.VARIABLE_NAME; }

{STRING_LITERAL}            { yybegin(S1);          return XPathTokenTypes.STRING_LITERAL; }
{NUMBER}                    { yybegin(S1);          return XPathTokenTypes.NUMBER; }

{NODE_TYPE} / {WS}? "("     { yybegin(S1);          return XPathTokenTypes.NODE_TYPE; }
{NODE_TYPE2} / {WS}? "("    { yybegin(S1);          return allowXPath2Syntax ? XPathTokenTypes.NODE_TYPE : XPathTokenTypes.FUNCTION_NAME; }

"for"                       { if (allowXPath2Syntax) { return XPath2TokenTypes.FOR;       } else { return ncName(); } }
"in"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.IN;        } else { return ncName(); } }
"to"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.TO;        } else { return ncName(); } }
"return"                    { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.RETURN;    } else { return ncName(); } }
"satisfies"                 { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.SATISFIES; } else { return ncName(); } }
"some"                      { if (allowXPath2Syntax) { return XPath2TokenTypes.SOME;      } else { return ncName(); } }
"every"                     { if (allowXPath2Syntax) { return XPath2TokenTypes.EVERY;     } else { return ncName(); } }
"if"                        { if (allowXPath2Syntax) { return XPath2TokenTypes.IF;        } else { return ncName(); } }
"if" / {WS}? "("            { if (allowXPath2Syntax) { return XPath2TokenTypes.IF;        } else { return XPathTokenTypes.FUNCTION_NAME; } }
"then"                      { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.THEN;      } else { return ncName(); } }
"else"                      { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.ELSE;      } else { return ncName(); } }
"union"                     { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.UNION; } else { return ncName(); } }
"intersect"                 { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.INTERSECT; } else { return ncName(); } }
"except"                    { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.EXCEPT;    } else { return ncName(); } }
"instance"                  { if (allowXPath2Syntax) { return XPath2TokenTypes.INSTANCE;  } else { return ncName(); } }
"of"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.OF;        } else { return ncName(); } }
"treat"                     { if (allowXPath2Syntax) { return XPath2TokenTypes.TREAT;     } else { return ncName(); } }
"as"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL); return XPath2TokenTypes.AS;        } else { return ncName(); } }
"castable"                  { if (allowXPath2Syntax) { return XPath2TokenTypes.CASTABLE;  } else { return ncName(); } }
"cast"                      { if (allowXPath2Syntax) { return XPath2TokenTypes.CAST;      } else { return ncName(); } }

<S1> "idiv"                 { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.IDIV; } else { return ncName(); } }

"is"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.IS;   } else { return ncName(); } }
"<<"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.BEFORE; } else { yypushback(1); return XPathTokenTypes.LT; } }
">>"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.AFTER; } else { yypushback(1); return XPathTokenTypes.GT; } }

"eq"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WEQ; } else { return ncName(); } }
"ne"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WNE; } else { return ncName(); } }
"lt"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WLT; } else { return ncName(); } }
"gt"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WGT; } else { return ncName(); } }
"le"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WLE; } else { return ncName(); } }
"ge"                        { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.WGE; } else { return ncName(); } }

"?"                         { if (allowXPath2Syntax) { yybegin(YYINITIAL);   return XPath2TokenTypes.QUEST; } else { return XPathTokenTypes.BAD_CHARACTER; } }

<YYINITIAL>
    {NCName} /
    {WS}? ":" {NCName}
    {WS}? "("               { yybegin(FUNC);        return XPathTokenTypes.EXT_PREFIX; }
<YYINITIAL>
    {NCName} / {WS}? "("    {                       return XPathTokenTypes.FUNCTION_NAME; }
<YYINITIAL>
    {NCName} / {WS}? "(:"   { if (allowXPath2Syntax) { yybegin(S1); return XPathTokenTypes.NCNAME; } else { return XPathTokenTypes.FUNCTION_NAME;  } }
<FUNC> ":"                  {                       return XPathTokenTypes.COL; }
<FUNC> {NCName}             {                       return XPathTokenTypes.FUNCTION_NAME; }

<S1> "*"                    { yybegin(YYINITIAL);   return XPathTokenTypes.MULT; }
<YYINITIAL> "*"             { yybegin(S1);          return XPathTokenTypes.STAR; }

<S1> "and"                  { yybegin(YYINITIAL);   return XPathTokenTypes.AND; }
<S1> "or"                   { yybegin(YYINITIAL);   return XPathTokenTypes.OR; }
<S1> "div"                  { yybegin(YYINITIAL);   return XPathTokenTypes.DIV; }
<S1> "mod"                  { yybegin(YYINITIAL);   return XPathTokenTypes.MOD; }

{AXIS}   / {WS}? "::"       { yybegin(S1);          return XPathTokenTypes.AXIS_NAME; }
{NCName} / {WS}? "::"       { yybegin(S1);          return XPathTokenTypes.BAD_AXIS_NAME; }

{NCName}                    { yybegin(S1);          return XPathTokenTypes.NCNAME; }

"@"                         { yybegin(YYINITIAL);   return XPathTokenTypes.AT; }
"::"                        { yybegin(YYINITIAL);   return XPathTokenTypes.COLCOL; }
","                         { yybegin(YYINITIAL);   return XPathTokenTypes.COMMA; }
"."                         { yybegin(S1);          return XPathTokenTypes.DOT; }
".."                        { yybegin(S1);          return XPathTokenTypes.DOTDOT; }
":"                         { yybegin(YYINITIAL);   return XPathTokenTypes.COL; }

"/"                         { yybegin(YYINITIAL);   return XPathTokenTypes.PATH; }
"//"                        { yybegin(YYINITIAL);   return XPathTokenTypes.ANY_PATH; }
"|"                         { yybegin(YYINITIAL);   return XPathTokenTypes.UNION; }

"+"                         { yybegin(YYINITIAL);   return XPathTokenTypes.PLUS; }
"-"                         { yybegin(YYINITIAL);   return XPathTokenTypes.MINUS; }
"="                         { yybegin(YYINITIAL);   return XPathTokenTypes.EQ; }
"!="                        { yybegin(YYINITIAL);   return XPathTokenTypes.NE; }
"<"  | "&lt;"               { yybegin(YYINITIAL);   return XPathTokenTypes.LT; }
">"  | "&gt;"               { yybegin(YYINITIAL);   return XPathTokenTypes.GT; }
"<=" | "&lt;="              { yybegin(YYINITIAL);   return XPathTokenTypes.LE; }
">=" | "&gt;="              { yybegin(YYINITIAL);   return XPathTokenTypes.GE; }

"("                         { yybegin(YYINITIAL);   return XPathTokenTypes.LPAREN; }
")"                         { yybegin(S1);          return XPathTokenTypes.RPAREN; }
"["                         { yybegin(YYINITIAL);   return XPathTokenTypes.LBRACKET; }
"]"                         { yybegin(S1);          return XPathTokenTypes.RBRACKET; }

"{"                         { return XPathTokenTypes.LBRACE; }
"}"                         { return XPathTokenTypes.RBRACE; }

.                           { yybegin(YYINITIAL);   return XPathTokenTypes.BAD_CHARACTER; }
parser grammar UnitParser;

options { tokenVocab = UnitLexer; }

singleUnit : IDENT;
scale : NUMBER;
singleOrScale : (singleUnit | scale) (POWER NUMBER)?;
unit : singleOrScale (WS unit | WS? DIVIDE WS? unit | WS? TIMES WS? unit)? | OPEN_BRACKET unit CLOSE_BRACKET;

display : (PREFIX | SUFFIX) STRING;

unitDeclaration : UNIT WS singleUnit WS (STRING WS?)? display (EQUALS WS? unit WS?)? NEWLINE;

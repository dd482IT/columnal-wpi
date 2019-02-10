parser grammar TransformationParser;

options { tokenVocab = TransformationLexer; }

item : ATOM | STRING;

expression: EXPRESSION_BEGIN EXPRESSION EXPRESSION_END;
value: VALUE_BEGIN VALUE VALUE_END;
type: TYPE_BEGIN TYPE;


/* Hide: */
hideKW : {_input.LT(1).getText().equals("HIDE")}? ATOM;
hideColumn : hideKW column=item NEWLINE;
hideColumns : hideColumn*;

/* Sort: */
orderKW : {_input.LT(1).getText().equals("ASCENDING") || _input.LT(1).getText().equals("DESCENDING")}? ATOM;
orderBy : orderKW column=item NEWLINE;
sort : orderBy*;

splitKW : {_input.LT(1).getText().equals("SPLIT")}? ATOM;
fromKW : {_input.LT(1).getText().equals("SUMMARY")}? ATOM;
summaryType : item;
summaryCol : fromKW column=item expression; // No newline because expression consumes it
splitBy : splitKW column=item NEWLINE;
summary : summaryCol* splitBy*;

/* Concat: */
concatIncompleteKW : {_input.LT(1).getText().equals("@INCOMPLETE")}? ATOM;
concatOmit : {_input.LT(1).getText().equals("OMIT")}? ATOM;
concatWrapMaybe : {_input.LT(1).getText().equals("WRAPMAYBE")}? ATOM;
concatDefault : {_input.LT(1).getText().equals("DEFAULT")}? ATOM;
concatIncludeSource : {_input.LT(1).getText().equals("SOURCE")}? ATOM;
concatMissing : concatIncompleteKW (concatOmit | concatWrapMaybe | concatDefault) concatIncludeSource?;

/* Transform: */

transformCalculate : {_input.LT(1).getText().equals("CALCULATE")}? ATOM;
transformItem : transformCalculate column=item expression;
transform : transformItem*;
package records.error;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import records.grammar.DataLexer;
import records.grammar.DataParser;

/**
 * Created by neil on 14/01/2017.
 */
public class ParseException extends UserException
{
    public ParseException(ParserRuleContext problemItem, String explanation)
    {
        super(formatLocation(problemItem) + " " + explanation + ": \"" + problemItem.getText() + "\"");
    }

    public ParseException(String expectedItem, Parser p)
    {
        super("Expected " + expectedItem + " found: {" + p.getCurrentToken().getText() + "} " + DataLexer.tokenNames[p.getCurrentToken().getType()]);
    }

    private static String formatLocation(ParserRuleContext problemItem)
    {
        if (problemItem.getStart().getLine() == problemItem.getStop().getLine())
            return "Line " + problemItem.getStart().getLine() + ", Columns " + problemItem.getStart().getCharPositionInLine() + " to " + problemItem.getStop().getCharPositionInLine();
        else
            return "Line " + problemItem.getStart().getLine() + " Column" + problemItem.getStart().getCharPositionInLine() + " to line " + problemItem.getStop().getLine() + " Column " + problemItem.getStop().getCharPositionInLine();
    }
}

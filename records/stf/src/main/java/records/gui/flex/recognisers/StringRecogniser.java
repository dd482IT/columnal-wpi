package records.gui.flex.recognisers;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.gui.flex.Recogniser;
import utility.Either;
import utility.Pair;

public class StringRecogniser extends Recogniser<@Value String>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@Value String>> process(ParseProgress orig)
    {
        ParseProgress pp = orig.consumeNext("\"");
        if (pp == null)
            return error("Looking for \" to begin text");
        Pair<String, ParseProgress> content = orig.consumeUpToAndIncluding("\"");
        if (content == null)
            return error("Could not find closing \" for text");
        return success(DataTypeUtility.value(content.getFirst()), content.getSecond());
    }
}
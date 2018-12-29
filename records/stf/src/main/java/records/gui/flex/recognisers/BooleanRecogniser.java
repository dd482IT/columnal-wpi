package records.gui.flex.recognisers;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.gui.flex.Recogniser;
import utility.Either;

public class BooleanRecogniser extends Recogniser<@Value Boolean>
{
    @Override
    public Either<ErrorDetails, SuccessDetails<@Value Boolean>> process(ParseProgress orig)
    {
        @Nullable ParseProgress pp = orig.consumeNextIC("true");
        if (pp != null)
            return success(DataTypeUtility.value(true), pp);
        pp = orig.consumeNextIC("false");
        if (pp != null)
            return success(DataTypeUtility.value(false), pp);
        
        return error("Expected true or false");
    }

}
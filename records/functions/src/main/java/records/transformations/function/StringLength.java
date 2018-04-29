package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

public class StringLength extends FunctionDefinition
{
    public StringLength() throws InternalException
    {
        super("text:text length");
    }

    @Override
    public ValueFunction getInstance(SimulationFunction<String, DataType> paramTypes) throws InternalException
    {
        return new Instance();
    }

    private static class Instance extends ValueFunction
    {

        @Override
        public @Value Object call(@Value Object param) throws UserException, InternalException
        {
            @Value String str = Utility.cast(param, String.class);
            return DataTypeUtility.value(str.codePointCount(0, str.length()));
        }
    }
}

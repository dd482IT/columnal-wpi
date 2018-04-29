package records.transformations.function;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import utility.SimulationFunction;
import utility.Utility;
import utility.ValueFunction;

public class StringTrim extends FunctionDefinition
{
    public StringTrim() throws InternalException
    {
        super("text:trim");
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
            String src = Utility.cast(param, String.class);
            // From https://stackoverflow.com/a/28295733
            return DataTypeUtility.value(src.replaceAll("(^(\\h|[\r\n])*)|((\\h|[\n\r])*$)",""));
        }
    }
}

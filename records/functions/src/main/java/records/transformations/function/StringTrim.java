package records.transformations.function;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.List;

public class StringTrim extends FunctionDefinition
{
    public StringTrim()
    {
        super("trim", "trim.mini", Instance::new, DataType.TEXT, DataType.TEXT);
    }
    
    public static FunctionGroup group()
    {
        return new FunctionGroup("trim.short", new StringTrim());
    }

    private static class Instance extends FunctionInstance
    {

        @Override
        public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException
        {
            String src = Utility.cast(param, String.class);
            // From https://stackoverflow.com/a/28295733
            return DataTypeUtility.value(src.replaceAll("(^(\\h|[\r\n])*)|((\\h|[\n\r])*$)",""));
        }
    }
}

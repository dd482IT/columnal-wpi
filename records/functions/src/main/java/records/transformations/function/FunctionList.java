package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static ImmutableList<FunctionGroup> getFunctions()
    {
        return ImmutableList.copyOf(Arrays.asList(
            Absolute.group(),
            //AsUnit.group(),
            Count.group(),
            GetElement.group(),
            Max.group(),
            Mean.group(),
            Min.group(),
            Not.group(),
            Round.group(),
            StringLeft.group(),
            StringLength.group(),
            StringMid.group(),
            StringReplaceAll.group(),
            StringRight.group(),
            StringTrim.group(),
            StringWithin.group(),
            StringWithinIndex.group(),
            Sum.group(),
            new ToDate(),
            new ToDateTime(),
            new ToDateTimeZone(),
            new ToTime(),
            new ToTimeAndZone(),
            new ToYearMonth()
        ));
    }

    public static @Nullable FunctionDefinition lookup(UnitManager mgr, String functionName) throws InternalException
    {
        for (FunctionGroup group : getFunctions())
        {
            for (FunctionDefinition functionDefinition : group.getFunctions(mgr))
            {
                if (functionDefinition.getName().equals(functionName))
                    return functionDefinition;
            }
        }
        return null;
    }
}

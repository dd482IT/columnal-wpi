package records.transformations.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.transformations.function.list.AnyAllNone;
import records.transformations.function.list.Count;
import records.transformations.function.list.GetElement;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static ImmutableList<FunctionGroup> getFunctionGroups()
    {
        return ImmutableList.copyOf(Arrays.asList(
            Absolute.group(),
            new AnyAllNone(),
            //AsUnit.group(),
            Count.group(),
            FromString.group(),
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
            ToString.group(),
            new ToDate(),
            new ToDateTime(),
            new ToDateTimeZone(),
            new ToTime(),
            new ToTimeAndZone(),
            new ToYearMonth()
        ));
    }
    
    public static ImmutableList<Pair<FunctionGroup, FunctionDefinition>> getAllFunctionDefinitions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<Pair<FunctionGroup, FunctionDefinition>> r = ImmutableList.builder();
        for (FunctionGroup group : getFunctionGroups())
        {
            r.addAll(Utility.mapList(group.getFunctions(mgr), f -> new Pair<>(group, f)));
        }
        return r.build();
    }

    public static @Nullable FunctionDefinition lookup(UnitManager mgr, String functionName) throws InternalException
    {
        for (FunctionGroup group : getFunctionGroups())
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

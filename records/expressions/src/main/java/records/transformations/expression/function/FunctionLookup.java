package records.transformations.expression.function;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;

public interface FunctionLookup
{
    public @Nullable StandardFunctionDefinition lookup(String functionName) throws InternalException;

    public ImmutableList<StandardFunctionDefinition> getAllFunctions() throws InternalException;
}
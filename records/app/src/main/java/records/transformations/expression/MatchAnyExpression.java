package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Status;
import records.gui.expressioneditor.OperandNode;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 23/02/2017.
 */
public class MatchAnyExpression extends NonOperatorExpression
{
    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ErrorRecorder onError) throws UserException, InternalException
    {
        // If normal check is called, something has gone wrong because we are only
        // valid in a pattern
        onError.recordError(this, "Any cannot be declared outside pattern match");
        return null;
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        throw new InternalException("Calling getValue on \"any\" pattern (should only call matchAsPattern)");
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.empty();
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@any";
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        return (p, s) -> new GeneralExpressionEntry("any", false, Status.ANY, p, s);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof MatchAnyExpression;
    }

    @Override
    public int hashCode()
    {
        return 0;
    }
}

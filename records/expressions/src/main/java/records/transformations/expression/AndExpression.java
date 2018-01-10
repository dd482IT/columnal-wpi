package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.types.TypeCons;
import records.types.TypeExp;
import utility.Pair;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class AndExpression extends NaryOpExpression
{
    public AndExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new AndExpression(replacements);
    }

    @Override
    protected String saveOp(int index)
    {
        return "&";
    }

    @Override
    public @Nullable @Recorded TypeExp check(RecordSet data, TypeState state, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeExp bool = TypeExp.fromConcrete(this, DataType.BOOLEAN);
        for (Expression expression : expressions)
        {
            @Nullable TypeExp type = expression.check(data, state, onError);
            if (type == null || onError.recordError(this, TypeExp.unifyTypes(bool, type)) == null)
                return null;
        }
        return onError.recordType(this, bool);
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        for (Expression expression : expressions)
        {
            Boolean b = (Boolean) expression.getValue(rowIndex, state);
            if (b == false)
                return DataTypeUtility.value(false);
        }
        return DataTypeUtility.value(true);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        int index = r.nextInt(expressions.size());
        return copy(makeNullList(index, newExpressionOfDifferentType.getDifferentType(new TypeCons(null, TypeExp.CONS_BOOLEAN))));
    }
}

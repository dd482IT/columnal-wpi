package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 29/11/2016.
 */
public class TimesExpression extends NaryOpExpression
{
    public TimesExpression(List<Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public NaryOpExpression copyNoNull(List<Expression> replacements)
    {
        return new TimesExpression(replacements);
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Rational running = Rational.ONE;
        for (Expression expression : expressions)
        {
            Optional<Rational> r = expression.constantFold();
            if (r.isPresent())
                running = running.times(r.get());
            else
                return Optional.empty();
        }
        return Optional.of(running);
    }

    @Override
    protected String saveOp(int index)
    {
        return "*";
    }

    @Override
    public @Nullable DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        Unit runningUnit = Unit.SCALAR;
        int minDP = 0;
        for (Expression expression : expressions)
        {
            @Nullable DataType expType = expression.check(data, state, onError);
            if (expType == null)
                return null;
            if (!expType.isNumber())
            {
                onError.accept(expression, "Non-numeric type in multiplication expression: " + expType);
                return null;
            }
            NumberInfo numberInfo = expType.getNumberInfo();
            runningUnit = runningUnit.times(numberInfo.getUnit());
            minDP = Math.max(minDP, numberInfo.getMinimumDP());
        }
        return DataType.number(new NumberInfo(runningUnit, minDP));
    }

    @Override
    public @OnThread(Tag.Simulation) Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        Number n = (Number) expressions.get(0).getValue(rowIndex, state);
        for (int i = 1; i < expressions.size(); i++)
            n = Utility.multiplyNumbers(n, (Number) expressions.get(i).getValue(rowIndex, state));
        return n;
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
        return copy(makeNullList(index, newExpressionOfDifferentType.getNonNumericType()));
    }
}

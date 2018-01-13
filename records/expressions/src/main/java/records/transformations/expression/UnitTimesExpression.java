package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import records.types.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitTimesExpression extends UnitExpression
{
    private final ImmutableList<UnitExpression> operands;

    public UnitTimesExpression(ImmutableList<UnitExpression> operands)
    {
        this.operands = operands;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, UnitExp> r = Either.right(UnitExp.SCALAR);
        for (UnitExpression operand : operands)
        {
            r = r.flatMap(u -> operand.asUnit(unitManager).map(v -> u.times(v)));
        }
        return r;
    }

    @Override
    public String save(boolean topLevel)
    {
        StringBuilder b = new StringBuilder();
        if (!topLevel)
            b.append("(");
        for (int i = 0; i < operands.size(); i++)
        {
            b.append(operands.get(i).save(false));
            if (i < operands.size() - 1)
            {
                b.append("*");
            }
        }
        if (!topLevel)
            b.append(")");
        return b.toString();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression, UnitNodeParent> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }

    public ImmutableList<UnitExpression> getOperands()
    {
        return operands;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitTimesExpression that = (UnitTimesExpression) o;

        return operands.equals(that.operands);
    }

    @Override
    public int hashCode()
    {
        return operands.hashCode();
    }
}

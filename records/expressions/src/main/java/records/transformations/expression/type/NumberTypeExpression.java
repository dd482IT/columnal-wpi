package records.transformations.expression.type;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyUnit;
import records.transformations.expression.UnitExpression;
import styled.StyledString;
import utility.Pair;

import java.util.List;
import java.util.Objects;

public class NumberTypeExpression extends TypeExpression
{
    private final @Nullable UnitExpression unitExpression;

    public NumberTypeExpression(@Nullable UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        if (unitExpression == null || unitExpression.isEmpty() || unitExpression.isScalar())
            return "Number";
        else
            return "Number {" + unitExpression.save(structured, true) + "}"; 
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return unitExpression == null ? DataType.NUMBER : unitExpression.asUnit(typeManager.getUnitManager())
            .<@Nullable DataType>either(err -> null, jellyUnit -> {
                try
                {
                    return DataType.number(new NumberInfo(jellyUnit.makeUnit(ImmutableMap.of())));
                }
                catch (InternalException e)
                {
                    return null;
                }
            });
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UnJellyableTypeExpression
    {
        if (unitExpression == null)
            return JellyType.number(JellyUnit.fromConcrete(Unit.SCALAR));
        
        return unitExpression.asUnit(typeManager.getUnitManager())
            .<JellyType, InternalException, UnJellyableTypeExpression>eitherEx2((Pair<@Nullable StyledString, List<UnitExpression>> p) -> {throw new UnJellyableTypeExpression(p.getFirst() == null ? "Invalid unit" : p.getFirst().toPlain());}, JellyType::number);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public StyledString toStyledString()
    {
        if (unitExpression == null)
            return StyledString.s("Number");
        else
            return StyledString.concat(StyledString.s("Number{"), unitExpression.toStyledString(), StyledString.s("}"));
    }

    public @Nullable UnitExpression _test_getUnits()
    {
        return unitExpression;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumberTypeExpression that = (NumberTypeExpression) o;
        return Objects.equals(unitExpression, that.unitExpression)
            || (unitExpression == null && that.unitExpression != null && that.unitExpression.isScalar())
            || (that.unitExpression == null && unitExpression != null && unitExpression.isScalar());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unitExpression);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    public boolean hasUnit()
    {
        return unitExpression != null;
    }
}

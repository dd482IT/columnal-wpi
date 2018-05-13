package records.typeExp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;

import java.util.List;
import java.util.Objects;

public class TypeCons extends TypeExp
{
    public final String name;
    // Can be size 0+:
    public final ImmutableList<Either<UnitExp, TypeExp>> operands;
    
    // If operands is empty, this is the actual set of type-classes.  If operands is non-empty,
    // this is the list of type-classes which can be derived as long as all of the inner types
    // satisfy them (bit of a hack, but it will do for now)
    private final ImmutableSet<String> typeClasses;

    public TypeCons(@Nullable ExpressionBase src, String name, ImmutableSet<String> typeClasses)
    {
        super(src);
        this.name = name;
        this.operands = ImmutableList.of();
        this.typeClasses = typeClasses;
    }
    
    public TypeCons(@Nullable ExpressionBase src, String name, ImmutableList<Either<UnitExp, TypeExp>> operands, ImmutableSet<String> derivableTypeClasses)
    {
        super(src);
        this.name = name;
        this.operands = operands;
        this.typeClasses = derivableTypeClasses;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof TypeCons))
            return typeMismatch(b);
        
        TypeCons bt = (TypeCons) b;
        if (!name.equals(bt.name))
            return typeMismatch(b);
        
        // This probably shouldn't happen in our editor, as it suggests
        // an incoherent expression:
        if (operands.size() != bt.operands.size())
            return typeMismatch(b);

        ImmutableList.Builder<Either<UnitExp, TypeExp>> unifiedOperands = ImmutableList.builder();
        for (int i = 0; i < operands.size(); i++)
        {
            Either<UnitExp, TypeExp> us = operands.get(i);
            Either<UnitExp, TypeExp> them = bt.operands.get(i);
            
            if (us.isLeft() && them.isLeft())
            {
                @Nullable UnitExp unified = us.getLeft().unifyWith(them.getLeft());
                if (unified == null)
                    return Either.left(StyledString.s("Cannot match units " + us.getLeft() + " with " + them.getLeft()));
                unifiedOperands.add(Either.left(unified));
            }
            else if (us.isRight() && them.isRight())
            {
                Either<StyledString, TypeExp> sub = us.getRight().unifyWith(them.getRight());
                if (sub.isLeft())
                    return sub;
                unifiedOperands.add(Either.right(sub.getRight()));
            }
            else
            {
                return Either.left(StyledString.s("Cannot match units with a type"));
            }
        }
        return Either.right(new TypeCons(src != null ? src : b.src, name, unifiedOperands.build(), ImmutableSet.copyOf(Sets.intersection(typeClasses, ((TypeCons) b).typeClasses))));
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        ImmutableList.Builder<Either<UnitExp, TypeExp>> without = ImmutableList.builder();
        for (Either<UnitExp, TypeExp> operand : operands)
        {
            @Nullable Either<UnitExp, TypeExp> e = operand.<@Nullable Either<UnitExp, TypeExp>>either(u -> Either.left(u), t -> {
                @Nullable TypeExp r = t.withoutMutVar(mutVar);
                return r == null ? null : Either.right(r);
            });
            if (e == null)
                return null;
            without.add(e);
        }
        return new TypeCons(src, name, without.build(), typeClasses);
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager) throws InternalException, UserException
    {
        switch (name)
        {
            case CONS_TEXT:
                return Either.right(DataType.TEXT);
            case CONS_BOOLEAN:
                return Either.right(DataType.BOOLEAN);
            case CONS_LIST:
                if (operands.get(0).isLeft())
                    throw new UserException("List must be of a type, not a unit");
                else
                    return operands.get(0).getRight().toConcreteType(typeManager).map(t -> DataType.array(t));
            case CONS_FUNCTION:
                if (operands.get(0).isLeft() || operands.get(1).isLeft())
                    throw new UserException("Function cannot take or return a unit");
                Either<TypeConcretisationError, DataType> arg = operands.get(0).getRight().toConcreteType(typeManager);
                if (arg.isLeft())
                    return arg;
                Either<TypeConcretisationError, DataType> ret = operands.get(1).getRight().toConcreteType(typeManager);
                if (ret.isLeft())
                    return ret;
                return Either.right(DataType.function(arg.getRight(), ret.getRight()));
            default:
                try
                {
                    return Either.right(DataType.date(new DateTimeInfo(DateTimeType.valueOf(name))));
                }
                catch (IllegalArgumentException e) // Thrown by valueOf()
                {
                    // Not a date type, continue...
                }
                Either<TypeConcretisationError, List<Either<Unit, DataType>>> errOrOperandsAsTypes = Either.mapMEx(operands, o -> {
                    // So, the outer either here is for units versus types, but the return type is either error or either-unit-or-type.
                    return o.eitherEx(u -> Either.right(Either.left(u.toConcreteUnit())), t -> t.toConcreteType(typeManager).map(Either::right));
                });
                return errOrOperandsAsTypes.eitherEx(err -> Either.left(new TypeConcretisationError(err.getErrorText(), null)), (List<Either<Unit, DataType>> operandsAsTypes) -> {
                    @Nullable DataType tagged = typeManager.lookupType(new TypeId(name), ImmutableList.copyOf(operandsAsTypes));
                    if (tagged != null)
                    {
                        return Either.right(tagged);
                    }
                    return Either.left(new TypeConcretisationError(StyledString.s("Unknown type constructor: " + name)));
                });
                
        }
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses)
    {
        if (operands.isEmpty())
        {
            return typeClasses.checkIfSatisfiedBy(toStyledString(), this.typeClasses);
            
            /*
                StyledString.Builder b = StyledString.builder();
                b.append("Type: ");
                b.append(name).append(operands.stream().map(s -> StyledString.concat(StyledString.s("-"), s.toStyledString())).collect(StyledString.joining("")));
                b.append(" is not " + Sets.difference(typeClasses, this.typeClasses).stream().collect(Collectors.joining(" or ")));
                return b.build();
                */
        }
        else
        {
            // Apply all type constraints to children.
            // First check that everything they want can be derived:
            @Nullable StyledString derivationError = typeClasses.checkIfSatisfiedBy(toStyledString(), this.typeClasses);
            if (derivationError == null)
            {
                // Apply constraints to children so that they know them
                // for future unification:
                for (Either<UnitExp, TypeExp> operand : operands)
                {
                    @Nullable StyledString err = operand.<@Nullable StyledString>either(u -> null, t -> t.requireTypeClasses(typeClasses));
                    if (err != null)
                        return err;
                }
                return null;
            }
            else
            {
                return derivationError;
            }
        }
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s(name), operands.isEmpty() ? StyledString.s("") : StyledString.concat(operands.stream().map(t -> StyledString.concat(StyledString.s("-("), t.either(UnitExp::toStyledString, TypeExp::toStyledString), StyledString.s(")"))).toArray(StyledString[]::new)));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeCons typeCons = (TypeCons) o;
        return Objects.equals(name, typeCons.name) &&
            Objects.equals(operands, typeCons.operands);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, operands);
    }
}
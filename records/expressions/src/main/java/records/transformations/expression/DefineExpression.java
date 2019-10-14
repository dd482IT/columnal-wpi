package records.transformations.expression;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefineExpression extends Expression
{
    public static class Definition
    {
        public final @Recorded Expression lhsPattern;
        public final @Recorded Expression rhsValue;

        public Definition(@Recorded Expression lhsPattern, @Recorded Expression rhsValue)
        {
            this.lhsPattern = lhsPattern;
            this.rhsValue = rhsValue;
        }

        public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            CheckedExp rhs = rhsValue.check(dataLookup, typeState, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
            if (rhs == null)
                return null;
            CheckedExp lhs = lhsPattern.check(dataLookup, typeState, ExpressionKind.PATTERN, LocationInfo.UNIT_DEFAULT, onError);
            if (lhs == null)
                return null;
            
            // Need to unify:
            return onError.recordTypeAndError(lhsPattern, TypeExp.unifyTypes(lhs.typeExp, rhs.typeExp), lhs.typeState);
        }

        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState evaluate(EvaluateState state) throws InternalException, UserException
        {
            ValueResult valueResult = rhsValue.calculateValue(state);
            valueResult = lhsPattern.matchAsPattern(valueResult.value, valueResult.evaluateState);
            if (Utility.cast(valueResult.value, Boolean.class))
                return valueResult.evaluateState;
            else
                return null;
        }

        public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return lhsPattern.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames) + " = " + rhsValue.save(saveDestination, BracketedStatus.NEED_BRACKETS, renames);
        }

        @SuppressWarnings("recorded")
        public Definition replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new Definition(lhsPattern.replaceSubExpression(toReplace, replaceWith), rhsValue.replaceSubExpression(toReplace, replaceWith));
        }

        public StyledString toDisplay(DisplayType displayType, ExpressionStyler expressionStyler)
        {
            return StyledString.concat(
                lhsPattern.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler),
                StyledString.s(" = "),
                rhsValue.toDisplay(displayType, BracketedStatus.NEED_BRACKETS, expressionStyler)
            );
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Definition that = (Definition) o;
            return lhsPattern.equals(that.lhsPattern) &&
                    rhsValue.equals(that.rhsValue);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(lhsPattern, rhsValue);
        }
    }
    
    public static class DefineItem
    {
        public final Either<@Recorded HasTypeExpression, Definition> typeOrDefinition;
        public final CanonicalSpan trailingCommaOrThenLocation;

        public DefineItem(Either<@Recorded HasTypeExpression, Definition> typeOrDefinition, CanonicalSpan trailingCommaOrThenLocation)
        {
            this.typeOrDefinition = typeOrDefinition;
            this.trailingCommaOrThenLocation = trailingCommaOrThenLocation;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DefineItem that = (DefineItem) o;
            return typeOrDefinition.equals(that.typeOrDefinition);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(typeOrDefinition);
        }
    }

    private final CanonicalSpan defineLocation;
    // List will not be empty for a valid define:
    private final ImmutableList<DefineItem> defines;
    private final @Recorded Expression body;
    private final CanonicalSpan endLocation;
    
    public DefineExpression(CanonicalSpan defineLocation, ImmutableList<DefineItem> defines, 
                            @Recorded Expression body,
                            CanonicalSpan endLocation)
    {
        this.defineLocation = defineLocation;
        this.defines = defines;
        this.body = body;
        this.endLocation = endLocation;
    }

    public static DefineExpression unrecorded(ImmutableList<Either<@Recorded HasTypeExpression, Definition>> defines, @Recorded Expression body)
    {
        CanonicalSpan dummy = new CanonicalSpan(CanonicalLocation.ZERO, CanonicalLocation.ZERO);
        return new DefineExpression(dummy, Utility.mapListI(defines, d -> new DefineItem(d, dummy)), body, dummy);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, final TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        TypeState typeState = original;
        
        HashSet<String> shouldBeDeclaredInNextDefine = new HashSet<>();
        
        for (DefineItem defineItem : defines)
        {
            TypeState typeStateThisTime = typeState;
            Either<@Recorded HasTypeExpression, Definition> define = defineItem.typeOrDefinition;
            @Nullable CheckedExp checkEq = define.<@Nullable CheckedExp>eitherEx(hasType -> {
                CheckedExp checkedExp = hasType.check(dataLookup, typeStateThisTime, ExpressionKind.EXPRESSION, LocationInfo.UNIT_DEFAULT, onError);
                if (checkedExp == null)
                    return null;
                @ExpressionIdentifier String varName = hasType.getVarName();
                if (varName == null)
                {
                    // Should already be error
                    return null;
                }
                else if (!shouldBeDeclaredInNextDefine.add(varName))
                {
                    onError.recordError(hasType, StyledString.s("Duplicate type for variable " + varName));
                    return null;
                }
                return checkedExp;
            }, equal -> {
                // We observe the declared variables by differencing TypeState before and after:
                CheckedExp checkedExp = equal.check(dataLookup, typeStateThisTime, onError);
                if (checkedExp != null)
                {
                    Set<String> declared = Sets.difference(checkedExp.typeState.getAvailableVariables(), typeStateThisTime.getAvailableVariables());
                    Set<String> typedButNotDeclared = Sets.difference(shouldBeDeclaredInNextDefine, declared);
                    if (!typedButNotDeclared.isEmpty())
                    {
                        onError.recordError(equal.lhsPattern, StyledString.s("Type was given above for " + typedButNotDeclared.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
                        return null;
                    }
                    if (declared.isEmpty())
                    {
                        onError.recordError(equal.lhsPattern, StyledString.s("No new variables were declared"));
                        return null;
                    }
                }
                shouldBeDeclaredInNextDefine.clear();
                return checkedExp;
            });
            if (checkEq == null)
                return null;
            typeState = checkEq.typeState;
        }

        if (!shouldBeDeclaredInNextDefine.isEmpty())
        {
            onError.recordError(defines.get(defines.size() - 1).typeOrDefinition.<Expression>either(x -> x, x -> x.lhsPattern), StyledString.s("Type was given for " + shouldBeDeclaredInNextDefine.stream().collect(Collectors.joining(", ")) + " but variable(s) were not declared"));
            return null;
        }

        CheckedExp checkedBody = body.check(dataLookup, typeState, ExpressionKind.EXPRESSION, locationInfo, onError);
        if (checkedBody == null)
            return null;
        else
            return new CheckedExp(checkedBody.typeExp, original);
    }

    @Override
    public @OnThread(Tag.Simulation) ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        for (Definition define : Either.<@Recorded HasTypeExpression, Definition>getRights(Utility.<DefineItem, Either<@Recorded HasTypeExpression, Definition>>mapListI(defines, d -> d.typeOrDefinition)))
        {
            @Nullable EvaluateState outcome = define.evaluate(state);
            if (outcome == null)
            {
                throw new UserException(StyledString.concat(StyledString.s("Pattern did not match: "), define.lhsPattern.toStyledString()));
            }
            state = outcome;
        }
        return body.calculateValue(state);
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.define(this, defines, body);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "@define " + defines.stream().map(e -> e.typeOrDefinition.either(x -> x.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames), x -> x.save(saveDestination, renames))).collect(Collectors.joining(", ")) + " @then " + body.save(saveDestination, BracketedStatus.DONT_NEED_BRACKETS, renames) + " @enddefine";
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefineExpression that = (DefineExpression) o;
        return defines.equals(that.defines) &&
                body.equals(that.body);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(defines, body);
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.concat(
            StyledString.s("@define "),
            defines.stream().map(e -> e.typeOrDefinition.either(x -> x.toDisplay(displayType, BracketedStatus.DONT_NEED_BRACKETS, expressionStyler), x -> x.toDisplay(displayType, expressionStyler))).collect(StyledString.joining(", ")),
            StyledString.s(" @then "),
            body.toStyledString(),
            StyledString.s(" @enddefine")
        ), this);
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return DefineExpression.unrecorded(Utility.mapListI(defines, e -> e.typeOrDefinition.mapBoth(x -> (HasTypeExpression)x.replaceSubExpression(toReplace, replaceWith), x -> x.replaceSubExpression(toReplace, replaceWith))), body.replaceSubExpression(toReplace, replaceWith));
    }

    public CanonicalSpan getEndLocation()
    {
        return endLocation;
    }
}

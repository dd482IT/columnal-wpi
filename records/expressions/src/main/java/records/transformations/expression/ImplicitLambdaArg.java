package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.visitor.ExpressionVisitor;
import records.typeExp.MutVar;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import records.transformations.expression.function.ValueFunction;

import java.util.Random;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The question mark that makes an implicit lambda, e.g. in ? < 5
 */
public class ImplicitLambdaArg extends NonOperatorExpression
{
    private int id = -1;
    
    public ImplicitLambdaArg()
    {
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState typeState, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        assignId(typeState);
        ImmutableList<TypeExp> questTypes = typeState.findVarType(getVarName());
        if (questTypes == null || questTypes.isEmpty())
            throw new UserException("? is not a valid expression by itself");
        // Pick last one in case of nested definitions:
        return onError.recordType(this, typeState, questTypes.get(questTypes.size() - 1));
    }

    protected void assignId(TypeState typeState)
    {
        if (id < 0)
            id = typeState.getNextLambdaId();
    }

    protected String getVarName()
    {
        return "?" + id;
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        return result(state.get(getVarName()), state);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return "?";
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
        return o instanceof ImplicitLambdaArg;
    }

    @Override
    public int hashCode()
    {
        return 1;
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s("?"), this);
    }

    // If any of the list are implicit lambda args ('?'), returns a new type state
    // with a type for '?' and a wrap function which will turn the item into a function.
    // If none are, returns null and unaltered type state.
    protected static Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState> detectImplicitLambda(Expression src, ImmutableList<@Recorded Expression> args, TypeState typeState, ErrorAndTypeRecorder errorAndTypeRecorder)
    {
        ImmutableList<@Recorded ImplicitLambdaArg> lambdaArgs = getLambdaArgsFrom(args);
        
        if (!lambdaArgs.isEmpty())
        {
            ImmutableList<TypeExp> argTypes = Utility.mapListI(lambdaArgs, arg -> new MutVar(arg));
            return new Pair<@Nullable UnaryOperator<@Recorded TypeExp>, TypeState>(t -> errorAndTypeRecorder.recordTypeNN(src, TypeExp.function(src, argTypes, t)), typeState.addImplicitLambdas(lambdaArgs, argTypes));
        }
        else
        {
            return new Pair<>(null, typeState);
        }
    }
    
    /**
     * Given a list which may contain one or more implicit
     * arguments, form a function which takes a tuple of
     * them as arguments and stores them in evaluate state
     * before calling the given body.
     * If no lambda args present, calls body directly.
     * 
     * @param possibleArgs A list, some of which may be implicitlambdaarg (but not all)
     * @param body
     * @return
     */
    @OnThread(Tag.Simulation)
    public static ValueResult makeImplicitFunction(Expression outer, ImmutableList<@Recorded Expression> possibleArgs, EvaluateState state, SimulationFunction<EvaluateState, ValueResult> body) throws UserException, InternalException
    {
        ImmutableList<@Recorded ImplicitLambdaArg> lambdaArgs = getLambdaArgsFrom(possibleArgs);
        if (lambdaArgs.size() == 0)
        {
            // Not an implicit lambda, we shouldn't have been called,
            // but there is a sensible result to give:
            return body.apply(state);
        }
        else
        {
            // Takes one or more parameters:
            return outer.explanation(ValueFunction.value(new ValueFunction()
            {
                @Override
                public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                {
                    EvaluateState argState = state;
                    for (int i = 0; i < lambdaArgs.size(); i++)
                    {
                        argState = argState.add(lambdaArgs.get(i).getVarName(), arg(i));
                    }
                    ValueResult r = body.apply(argState);
                    addExtraExplanation(() -> r.makeExplanation(ExecutionType.CALL_IMPLICIT));
                    return r.value;
                }
            }), ExecutionType.VALUE, state, ImmutableList.of(), ImmutableList.of(), false);
        }
    }

    public static ImmutableList<@Recorded ImplicitLambdaArg> getLambdaArgsFrom(ImmutableList<@Recorded Expression> possibleArgs)
    {
        return Utility.<@Recorded Expression, @Recorded ImplicitLambdaArg>filterClass(possibleArgs.stream(), (Class<@Recorded ImplicitLambdaArg>)ImplicitLambdaArg.class).collect(ImmutableList.<@Recorded ImplicitLambdaArg>toImmutableList());
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.implicitLambdaArg(this);
    }
}

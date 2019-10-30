package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression.ExpressionStyler;
import records.transformations.expression.Expression.ValueResult;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.Explanation.ExecutionType;
import records.transformations.expression.explanation.ExplanationLocation;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.Set;
import java.util.function.Function;

/**
 * An exception which can produce a stack trace in terms of our own Expression items.
 */
public class EvaluationException extends UserException
{
    private static class StackEntry
    {
        private final Expression expression;
        private final ImmutableList<ValueResult> subValues;
        private final ExecutionType executionType;
        private final EvaluateState evaluateState;

        public StackEntry(Expression expression, ImmutableList<ValueResult> subValues, ExecutionType executionType, EvaluateState evaluateState)
        {
            this.expression = expression;
            this.subValues = subValues;
            this.executionType = executionType;
            this.evaluateState = evaluateState;
        }
    }
    
    // Originator (top of call stack) first, outermost call is last.  Will never be empty:
    private final ImmutableList<StackEntry> stack;

    // If e is an EvaluationException this is a chained catch.
    // Otherwise this is top of a new stack
    public EvaluationException(UserException e, Expression expression, ExecutionType executionType, EvaluateState evaluateState, ImmutableList<ValueResult> subItems)
    {
        super(e.getStyledMessage());
        StackEntry entry = new StackEntry(expression, subItems, executionType, evaluateState);
        if (e instanceof EvaluationException)
        {
            this.stack = Utility.appendToList(((EvaluationException) e).stack, entry);
        }
        else
        {
            this.stack = ImmutableList.of(entry);
        }
    }

    public Explanation makeExplanation()
    {
        return makeExplanation(stack.size() - 1);
    }
    
    private Explanation makeExplanation(int stackLevel)
    {
        StackEntry cur = stack.get(stackLevel);
        return new Explanation(cur.expression, cur.executionType, cur.evaluateState, null, ImmutableList.of(), null)
        {
            @Override
            public @Nullable StyledString describe(Set<Explanation> alreadyDescribed, Function<ExplanationLocation, StyledString> hyperlinkLocation, ExpressionStyler expressionStyler, ImmutableList<ExplanationLocation> extraLocations, boolean skipIfTrivial) throws InternalException, UserException
            {
                if (stackLevel == 0)
                    return StyledString.concat(getStyledMessage(), StyledString.s(" in: "), cur.expression.toStyledString());
                else
                    return StyledString.concat(StyledString.s("In: "), cur.expression.toStyledString());
            }

            @Override
            public ImmutableList<Explanation> getDirectSubExplanations() throws InternalException
            {
                ImmutableList<@NonNull Explanation> successful = Utility.mapListInt(cur.subValues, r -> r.makeExplanation(null));
                return stackLevel == 0 ? successful : Utility.appendToList(successful, makeExplanation(stackLevel - 1));
            }
        };
    }
}

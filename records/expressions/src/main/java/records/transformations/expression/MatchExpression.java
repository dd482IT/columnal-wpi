package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ExpressionEditorUtil;
import records.gui.expressioneditor.ExpressionSaver;
import records.gui.expressioneditor.GeneralExpressionEntry;
import records.gui.expressioneditor.GeneralExpressionEntry.Keyword;
import records.transformations.expression.NaryOpExpression.TypeProblemDetails;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.StreamTreeBuilder;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 28/11/2016.
 */
public class MatchExpression extends NonOperatorExpression
{
    /**
     * A clause is a list of patterns, and an outcome expression
     */
    public class MatchClause
    {
        private final ImmutableList<Pattern> patterns;
        private final @Recorded Expression outcome;

        public MatchClause(ImmutableList<Pattern> patterns, @Recorded Expression outcome)
        {
            this.patterns = patterns;
            this.outcome = outcome;
        }

        public List<Pattern> getPatterns()
        {
            return patterns;
        }

        public Expression getOutcome()
        {
            return outcome;
        }

        /**
         * Returns pattern match type, outcome type
         */
        public @Nullable Pair<List<TypeExp>, TypeExp> check(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            TypeExp[] patternTypes = new TypeExp[patterns.size()];
            TypeState[] rhsStates = new TypeState[patterns.size()];
            if (patterns.isEmpty())
            {
                // Probably a test generation error:
                onError.recordError(MatchExpression.this, StyledString.s("Clause with no patterns"));
                return null;
            }
            for (int i = 0; i < patterns.size(); i++)
            {
                @Nullable CheckedExp ts = patterns.get(i).check(data, state, onError);
                if (ts == null)
                    return null;
                patternTypes[i] = ts.typeExp;
                rhsStates[i] = ts.typeState;
            }
            TypeState rhsState = TypeState.intersect(Arrays.asList(rhsStates));
            @Nullable CheckedExp outcomeType = outcome.check(data, rhsState, LocationInfo.UNIT_DEFAULT, onError);
            if (outcomeType == null)
                return null;
            else
            {
                if (outcomeType.expressionKind == ExpressionKind.PATTERN)
                {
                    onError.recordError(this, StyledString.s("Match clause outcome cannot be a pattern"));
                    return null;
                }
                return new Pair<>(Arrays.asList(patternTypes), outcomeType.typeExp);
            }
        }

        //Returns null if no match
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState matches(@Value Object value, EvaluateState state) throws UserException, InternalException
        {
            for (Pattern p : patterns)
            {
                EvaluateState newState = p.match(value, state);
                if (newState != null) // Did it match?
                    return newState;
            }
            return null;
        }

        public String save(boolean structured, TableAndColumnRenames renames)
        {
            return " @case " + patterns.stream().map(p -> p.save(structured, renames)).collect(Collectors.joining(" @orcase ")) + " @then " + outcome.save(structured, BracketedStatus.MISC, renames);
        }

        public StyledString toDisplay()
        {
            return StyledString.concat(
                StyledString.s(" case "),
                patterns.stream().map(p -> p.toDisplay()).collect(StyledString.joining(" or ")),
                StyledString.s(" then "),
                outcome.toDisplay(BracketedStatus.MISC)
            );
        }

        public MatchClause copy(MatchExpression e)
        {
            return e.new MatchClause(patterns, outcome); //TODO deep copy patterns
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MatchClause that = (MatchClause)o;

            if (!patterns.equals(that.patterns)) return false;
            return outcome.equals(that.outcome);

        }

        @Override
        public int hashCode()
        {
            int result = patterns.hashCode();
            result = 31 * result + outcome.hashCode();
            return result;
        }

        @OnThread(Tag.FXPlatform)
        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();

            boolean first = true;
            for (Pattern pattern : patterns)
            {
                r.add(GeneralExpressionEntry.load(first ? Keyword.CASE : Keyword.ORCASE));
                r.addAll(pattern.load());
                first = false;
            }
            r.add(GeneralExpressionEntry.load(Keyword.THEN));
            r.addAll(outcome.loadAsConsecutive(BracketedStatus.MISC));
            return r.stream();
        }

        @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
        public Function<MatchExpression, MatchClause> replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return me -> me.new MatchClause(Utility.mapListI(patterns, p -> p.replaceSubExpression(toReplace, replaceWith)), outcome.replaceSubExpression(toReplace, replaceWith));
        }
    }

    /**
     * A pattern is an expression, plus an optional guard
     */
    public static class Pattern
    {
        private final @Recorded Expression pattern;
        private final @Nullable @Recorded Expression guard;

        public Pattern(@Recorded Expression pattern, @Nullable @Recorded Expression guard)
        {
            this.pattern = pattern;
            this.guard = guard;
        }

        /**
         * Returns pattern type, and resulting type state (including any declared vars)
         */
        public @Nullable CheckedExp check(ColumnLookup data, TypeState state, ErrorAndTypeRecorder onError) throws InternalException, UserException
        {
            final @Nullable CheckedExp rhsState = pattern.check(data, state, LocationInfo.UNIT_CONSTRAINED, onError);
            if (rhsState == null)
                return null;
            // No need to check expression versus pattern, either is fine, but we will require Equatable either way:
            rhsState.requireEquatable(false);
            
            if (guard != null)
            {
                @Nullable CheckedExp type = guard.check(data, rhsState.typeState, LocationInfo.UNIT_DEFAULT, onError);
                if (type == null || onError.recordError(guard, TypeExp.unifyTypes(TypeExp.bool(guard), type.typeExp)) == null)
                {
                    return null;
                }
                return new CheckedExp(rhsState.typeExp, type.typeState, rhsState.expressionKind);
            }
            return rhsState;
        }

        // Returns non-null if it matched, null if it didn't match.
        @OnThread(Tag.Simulation)
        public @Nullable EvaluateState match(@Value Object value, EvaluateState state) throws InternalException, UserException
        {
            @Nullable EvaluateState newState = pattern.matchAsPattern(value, state);
            if (newState == null)
                return null;
            if (guard != null)
            {
                ValueResult valAndState = guard.calculateValue(newState);
                boolean b = Utility.cast(valAndState.value, Boolean.class);
                if (b)
                    return valAndState.evaluateState;
                else
                    return null;
            }
            return newState;
        }

        public String save(boolean structured, TableAndColumnRenames renames)
        {
            return pattern.save(structured, BracketedStatus.MISC, renames) + (guard == null ? "" : " @given " + guard.save(structured, BracketedStatus.MISC, renames));
        }

        public StyledString toDisplay()
        {
            StyledString patternDisplay = pattern.toDisplay(BracketedStatus.MISC);
            return guard == null ? patternDisplay : StyledString.concat(StyledString.s(" given "), guard.toDisplay(BracketedStatus.MISC));
        }

        public @Recorded Expression getPattern()
        {
            return pattern;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Pattern pattern1 = (Pattern) o;

            if (!pattern.equals(pattern1.pattern)) return false;
            return guard != null ? guard.equals(pattern1.guard) : pattern1.guard == null;
        }

        @Override
        public int hashCode()
        {
            int result = pattern.hashCode();
            result = 31 * result + (guard != null ? guard.hashCode() : 0);
            return result;
        }

        // Load pattern and guard
        @OnThread(Tag.FXPlatform)
        public Stream<SingleLoader<Expression, ExpressionSaver>> load()
        {
            StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
            r.addAll(pattern.loadAsConsecutive(BracketedStatus.MISC));
            if (guard != null)
            {
                r.add(GeneralExpressionEntry.load(Keyword.GIVEN));
                r.addAll(guard.loadAsConsecutive(BracketedStatus.MISC));
            }
            return r.stream();
        }

        public @Nullable Expression getGuard()
        {
            return guard;
        }

        @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
        public Pattern replaceSubExpression(Expression toReplace, Expression replaceWith)
        {
            return new Pattern(pattern.replaceSubExpression(toReplace, replaceWith), guard == null ? null : guard.replaceSubExpression(toReplace, replaceWith));
        }
    }

    private final @Recorded Expression expression;
    private final List<MatchClause> clauses;

    @SuppressWarnings("initialization") // Because we pass this to sub-clauses which we are creating.
    public MatchExpression(@Recorded Expression expression, List<Function<MatchExpression, MatchClause>> clauses)
    {
        this.expression = expression;
        this.clauses = Utility.<Function<MatchExpression, MatchClause>, MatchClause>mapList(clauses, f -> f.apply(this));
    }

    public Expression getExpression()
    {
        return expression;
    }

    public List<MatchClause> getClauses()
    {
        return clauses;
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        // It's type checked so can just copy first clause:
        @Value Object value = expression.calculateValue(state).value;
        for (MatchClause clause : clauses)
        {
            EvaluateState newState = clause.matches(value, state);
            if (newState != null)
            {
                // TODO use the children for explanations
                return new ValueResult(clause.outcome.calculateValue(newState).value, state);
            }
        }
        throw new UserException("No matching clause found in expression: \"" + save(true, BracketedStatus.MISC, TableAndColumnRenames.EMPTY) + "\"");
    }

    @Override
    public Stream<ColumnReference> allColumnReferences()
    {
        return Stream.<ColumnReference>concat(expression.allColumnReferences(), clauses.stream().<ColumnReference>flatMap(c -> c.outcome.allColumnReferences()));
    }

    @Override
    public Stream<String> allVariableReferences()
    {
        return Stream.<String>concat(expression.allVariableReferences(), clauses.stream().<String>flatMap(c -> c.outcome.allVariableReferences()));
    }

    @Override
    public String save(boolean structured, BracketedStatus surround, TableAndColumnRenames renames)
    {
        String inner = "@match " + expression.save(structured, BracketedStatus.MISC, renames) + clauses.stream().map(c -> c.save(structured, renames)).collect(Collectors.joining("")) + " @endmatch";
        return (surround == BracketedStatus.DIRECT_ROUND_BRACKETED || surround == BracketedStatus.TOP_LEVEL) ? inner : ("(" + inner + ")");
    }

    @Override
    public StyledString toDisplay(BracketedStatus surround)
    {
        StyledString inner = StyledString.concat(StyledString.s("match "), expression.toDisplay(BracketedStatus.MISC), clauses.stream().map(c -> c.toDisplay()).collect(StyledString.joining("")));
        return (surround == BracketedStatus.DIRECT_ROUND_BRACKETED || surround == BracketedStatus.TOP_LEVEL) ? inner : StyledString.roundBracket(inner);
    }

    @Override
    public @Nullable CheckedExp check(ColumnLookup dataLookup, TypeState state, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {        
        // Need to check several things:
        //   - That all of the patterns have the same type as the expression being matched
        //   - That all of the pattern guards have boolean type
        //   - That all of the outcome expressions have the same type as each other (and is what we will return)

        @Nullable CheckedExp srcType = expression.check(dataLookup, state, LocationInfo.UNIT_DEFAULT, onError);
        if (srcType == null)
            return null;
        if (srcType.expressionKind == ExpressionKind.PATTERN)
        {
            onError.recordError(this, StyledString.s("Cannot have pattern in the item to be matched"));
            return null;
        }
        // The Equatable checks are done on the patterns, not the source item, because e.g. if all patterns match
        // certain tuple item as @anything, we don't need Equatable on that value.

        if (clauses.isEmpty())
        {
            onError.recordError(this, StyledString.s("Must have at least one clause in a match"));
            return null;
        }
        
        // Add one extra for the srcType.  This size will be wrong if a clause
        // has multiple patterns, but it's only a hint.
        List<TypeExp> patternTypes = new ArrayList<>(1 + clauses.size());
        TypeExp[] outcomeTypes = new TypeExp[clauses.size()];
        // Includes the original source pattern:
        ImmutableList.Builder<@Recorded Expression> patternExpressions = ImmutableList.builderWithExpectedSize(patternTypes.size());
        // Note: patternTypes and patternExpressions should always be the same length.        
        patternTypes.add(srcType.typeExp);
        patternExpressions.add(expression);
        
        for (int i = 0; i < clauses.size(); i++)
        {
            patternExpressions.addAll(Utility.<Pattern, @Recorded Expression>mapList(clauses.get(i).getPatterns(), p -> p.getPattern()));
            @Nullable Pair<List<TypeExp>, TypeExp> patternAndOutcomeType = clauses.get(i).check(dataLookup, state, onError);
            if (patternAndOutcomeType == null)
                return null;
            patternTypes.addAll(patternAndOutcomeType.getFirst());
            outcomeTypes[i] = patternAndOutcomeType.getSecond();
        }
        ImmutableList<@Recorded Expression> immPatternExpressions = patternExpressions.build();
        
        for (int i = 0; i < immPatternExpressions.size(); i++)
        {
            Expression expression = immPatternExpressions.get(i);
            List<QuickFix<Expression, ExpressionSaver>> fixesForMatchingNumericUnits = ExpressionEditorUtil.getFixesForMatchingNumericUnits(state, new TypeProblemDetails(patternTypes.stream().map(p -> Optional.of(p)).collect(ImmutableList.<Optional<TypeExp>>toImmutableList()), immPatternExpressions, i));
            if (!fixesForMatchingNumericUnits.isEmpty())
            {
                // Must show an error to get the quick fixes to show:
                onError.recordError(expression, StyledString.s("Pattern match items must have matching items"));
                onError.recordQuickFixes(expression, fixesForMatchingNumericUnits);
            }
        }
        
        if (onError.recordError(this, TypeExp.unifyTypes(patternTypes)) == null)
            return null;

        // TypeState doesn't extend outside the match expression, so we discard and return original:
        return onError.recordTypeAndError(this, TypeExp.unifyTypes(outcomeTypes), ExpressionKind.EXPRESSION, state);
    }

    @Override
    public Stream<SingleLoader<Expression, ExpressionSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<Expression, ExpressionSaver>> r = new StreamTreeBuilder<>();
        r.add(GeneralExpressionEntry.load(Keyword.MATCH));
        r.addAll(expression.loadAsConsecutive(BracketedStatus.MISC));
        for (MatchClause clause : clauses)
        {
            r.addAll(clause.load());
        }
        r.add(GeneralExpressionEntry.load(Keyword.ENDMATCH));
        return r.stream();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MatchExpression that = (MatchExpression)o;

        if (!expression.equals(that.expression)) return false;
        return clauses.equals(that.clauses);

    }

    @Override
    public int hashCode()
    {
        int result = expression.hashCode();
        result = 31 * result + clauses.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        // TODO allow replacement within clauses
        return expression._test_allMutationPoints().map(p -> new Pair<Expression, Function<Expression, Expression>>(p.getFirst(), e -> new MatchExpression(p.getSecond().apply(e), Utility.<MatchClause, Function<MatchExpression, MatchClause>>mapList(clauses, c -> c::copy))));
    }

    @Override
    public @Nullable Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        if (toReplace == this)
            return replaceWith;
        else
            return new MatchExpression(expression.replaceSubExpression(toReplace, replaceWith), Utility.mapList(clauses, c -> c.replaceSubExpression(toReplace, replaceWith)));
    }
}

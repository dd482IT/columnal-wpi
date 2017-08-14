package records.gui.expressioneditor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.SingleLoader;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.MatchExpression.Pattern;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One case in a match expression.  May have several "or"-ed patterns, each of which may
 * have a "where" guard.  Will always have one outcome.
 */
public class ClauseNode extends DeepNodeTree implements EEDisplayNodeParent, EEDisplayNode, ExpressionNodeParent
{
    private final PatternMatchNode parent;
    private final VBox caseLabel;
    // Each item here is a pattern + guard pair.  You can have one or more in a clause:
    private final ObservableList<Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>>> matches;
    // This is the body of the clause:
    private final ConsecutiveBase<Expression, ExpressionNodeParent> outcome;

    private static enum SubType
    {
        PATTERN, GUARD, OUTCOME;

        public String getPrefixKeyword()
        {
            switch (this)
            {
                case PATTERN:
                    return "case";
                case GUARD:
                    return "where";
                case OUTCOME:
                    return "then";
            }
            return "";
        }
    }

    @SuppressWarnings("initialization") //Calling getParentStyles
    public ClauseNode(PatternMatchNode parent, @Nullable Pair<List<Pair<Expression, @Nullable Expression>>, Expression> patternsAndGuardsToOutcome)
    {
        this.parent = parent;
        this.caseLabel = ExpressionEditorUtil.keyword("case", "case", parent, getParentStyles());
        this.matches = FXCollections.observableArrayList();
        // Must initialize outcome first because updateNodes will use it:
        this.outcome = makeConsecutive(SubType.OUTCOME/*"\u2794"*/, patternsAndGuardsToOutcome == null ? null : patternsAndGuardsToOutcome.getSecond());
        outcome.prompt("value");
        listenToNodeRelevantList(matches);
        if (patternsAndGuardsToOutcome == null)
            this.matches.add(makeNewCase(null, null));
        else
        {
            for (Pair<Expression, @Nullable Expression> caseAndGuard : patternsAndGuardsToOutcome.getFirst())
            {
                this.matches.add(makeNewCase(caseAndGuard.getFirst(), caseAndGuard.getSecond()));
            }
        }

    }

    @NotNull
    private Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> makeNewCase(@Nullable Expression caseExpression, @Nullable Expression guardExpression)
    {
        Consecutive<Expression, ExpressionNodeParent> pattern = makeConsecutive(SubType.PATTERN, caseExpression);
        pattern.prompt("pattern");
        Consecutive<Expression, ExpressionNodeParent> guard = guardExpression == null ? null : makeConsecutive(SubType.GUARD, guardExpression);
        if (guard != null)
            guard.prompt("condition");
        return new Pair<>(pattern, guard);
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return matches.stream().flatMap((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> m) -> Utility.streamNullable(m.getFirst(), m.getSecond()));
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.<Node>concat(
            matches.stream().flatMap((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> Stream.concat(p.getFirst().nodes().stream(), p.getSecond() == null ? Stream.<@NonNull Node>empty() : p.getSecond().nodes().stream())),
            outcome.nodes().stream());
    }

    @Override
    protected void updateDisplay()
    {

    }

    @SuppressWarnings("initialization") // Because of Consecutive
    private Consecutive<Expression, ExpressionNodeParent> makeConsecutive(@UnknownInitialization(Object.class)ClauseNode this, SubType subType, @Nullable Expression startingContent)
    {
        return new Consecutive<Expression, ExpressionNodeParent>(ConsecutiveBase.EXPRESSION_OPS, this, ExpressionEditorUtil.keyword(subType.getPrefixKeyword(), "match", parent, getParentStyles()), null, "match", startingContent == null ? null : SingleLoader.withSemanticParent(startingContent.loadAsConsecutive(), this)) {

            @Override
            public void addOperandToRight(OperatorEntry<Expression, ExpressionNodeParent> rightOf, String operatorEntered, String initialContent, boolean focus)
            {
                boolean lastItem = Utility.indexOfRef(operators, rightOf) == operators.size() - 1;

                if (lastItem && operatorEntered.equals("or") && subType == SubType.GUARD || (subType == SubType.PATTERN && !patternHasGuard(this)))
                {
                    addNewClauseToRightOf(this).focusWhenShown();
                }
                else if (lastItem && operatorEntered.equals("where") && subType == SubType.PATTERN && !patternHasGuard(this))
                {
                    addGuardFor(this);
                }
                else if (lastItem && operatorEntered.equals("then") && isLastItemBeforeOutcome(this))
                {
                    outcome.focus(Focus.LEFT);
                }
                else if (lastItem && operatorEntered.equals("case") && subType == SubType.OUTCOME)
                {
                    ClauseNode.this.parent.addNewCaseToRightOf(ClauseNode.this).focusWhenShown();
                }
                else
                {
                    super.addOperandToRight(rightOf, operatorEntered, initialContent, focus);
                }
            }

            @Override
            public boolean isFocused()
            {
                return childIsFocused();
            }

            @Override
            protected ExpressionNodeParent getThisAsSemanticParent()
            {
                return ClauseNode.this;
            }
        };
    }

    private boolean isLastItemBeforeOutcome(Consecutive<Expression, ExpressionNodeParent> patternOrGuard)
    {
        // Shouldn't happen, but just in case:
        if (matches.isEmpty()) return false;

        Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> last = matches.get(matches.size() - 1);

        return last.getSecond() == patternOrGuard || (last.getSecond() == null && last.getFirst() == patternOrGuard);
    }

    private void addGuardFor(Consecutive<Expression, ExpressionNodeParent> pattern)
    {
        OptionalInt index = Utility.findFirstIndex(matches, (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> p.getFirst() == pattern);
        index.ifPresent(i -> matches.set(i, new Pair<>(pattern, makeConsecutive(SubType.GUARD, null))));
    }

    private EEDisplayNode addNewClauseToRightOf(Consecutive<Expression, ExpressionNodeParent> patternOrGuard)
    {
        int index = Utility.findFirstIndex(matches, (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> p.getFirst() == patternOrGuard || p.getSecond() == patternOrGuard).orElse(matches.size() - 1);
        // Shouldn't be missing but we just use a sensible default as backup measure

        Consecutive<Expression, ExpressionNodeParent> c = makeConsecutive(SubType.PATTERN, null);
        matches.add(index + 1, new Pair<>(c, null));
        return c;
    }

    // Returns true if there's a guard, false if not
    private boolean patternHasGuard(Consecutive<Expression, ExpressionNodeParent> pattern)
    {
        return matches.stream().filter((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> p.getFirst() == pattern).map((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> p.getSecond() != null).findFirst().orElse(false);
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            matches.get(0).getFirst().focus(side);
        else
        {
            Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> last = matches.get(matches.size() - 1);
            if (last.getSecond() != null)
                last.getSecond().focus(side);
            else
                last.getFirst().focus(side);
        }
    }

    /*
    @Override
    public @Nullable DataType getType(EEDisplayNode child)
    {
        if (child == outcome) // Can't predict, unless we consult sibling clauses or go two up
            return parent.getType(this);

        DataType matchedType = parent.getMatchType();
        for (Pair<Consecutive, Consecutive> match : matches)
        {
            if (match.getFirst() == child)
                return matchedType;
            if (match.getSecond() == child)
                return DataType.BOOLEAN; // Guard
        }
        // Not a child of ours!
        return null;
    }*/

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        // TODO
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(EEDisplayNode child)
    {
        //TODO union of clause variables or just the clause variable for guard
        // (and always mix in parent variables)
        ArrayList<Pair<String, @Nullable DataType>> vars = new ArrayList<>(parent.getAvailableVariables(this));

        Multimap<@NonNull String, @Nullable DataType> allClauseVars = null;
        for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
        {
            if (match.getFirst() == child)
                return vars; // Matching side only has access to parent vars
            List<Pair<String, @Nullable DataType>> newMatchVars = match.getFirst().getDeclaredVariables();
            if (match.getSecond() == child)
            {
                vars.addAll(newMatchVars);
                return vars;
            }
            // Otherwise keep intersection of vars from different clauses:
            if (allClauseVars == null)
            {
                allClauseVars = ArrayListMultimap.create();
                for (Pair<String, @Nullable DataType> newMatchVar : newMatchVars)
                {
                    allClauseVars.put(newMatchVar.getFirst(), newMatchVar.getSecond());
                }
            }
            else
            {
                ArrayListMultimap<@NonNull String, @Nullable DataType> newAllClauseVars = ArrayListMultimap.create();
                for (Pair<String, @Nullable DataType> newMatchVar : newMatchVars)
                {
                    // Key must be in newMatchVars and allClauseVars, and if so we retain both types
                    if (allClauseVars.containsKey(newMatchVar.getFirst()))
                    {
                        newAllClauseVars.putAll(newMatchVar.getFirst(), allClauseVars.get(newMatchVar.getFirst()));
                        newAllClauseVars.put(newMatchVar.getFirst(), newMatchVar.getSecond());
                    }
                }
                allClauseVars = newAllClauseVars;
            }
        }
        if (outcome == child)
        {
            if (allClauseVars != null)
            {
                for (Entry<String, Collection<@Nullable DataType>> varType : allClauseVars.asMap().entrySet())
                {
                    // If all types are non-null and the same, add as known type
                    // Otherwise, must add null (unknown type).
                    // This is the behaviour of checkAllSame so we can just use that:
                    @Nullable DataType t;
                    try
                    {
                        List<DataType> nonNull = new ArrayList<>();
                        for (@Nullable DataType dataType : varType.getValue())
                        {
                            if (dataType != null)
                                nonNull.add(dataType);
                        }
                        if (nonNull.size() == varType.getValue().size())
                            t = DataType.checkAllSame(nonNull, s -> {});
                        else
                            t = null;
                    }
                    catch (InternalException | UserException e)
                    {
                        // Don't worry too much about it, just give unknown type:
                        t = null;
                    }
                    vars.add(new Pair<>(varType.getKey(), t));
                }
            }
            return vars;
        }
        Utility.logStackTrace("Unknown child: " + child);
        return vars;
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == outcome)
        {
            parent.focusRightOf(this);
        }
        else
        {
            boolean focusNext = false;
            for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
            {
                if (focusNext)
                {
                    match.getFirst().focus(Focus.LEFT);
                    return;
                }
                if (match.getFirst() == child)
                {
                    if (match.getSecond() != null)
                    {
                        match.getSecond().focus(Focus.LEFT);
                        return;
                    }
                    else
                        focusNext = true;
                }
                if (match.getSecond() == child)
                {
                    focusNext = true;
                }
            }
            if (focusNext)
                parent.focusRightOf(this);
        }
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == outcome)
        {
            Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> last = matches.get(matches.size() - 1);
            if (last.getSecond() != null)
                last.getSecond().focus(Focus.RIGHT);
            else
                last.getFirst().focus(Focus.RIGHT);
        }
        else
        {
            boolean focusEarlier = false;
            for (ListIterator<Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>>> iterator = matches.listIterator(); iterator.hasPrevious(); )
            {
                Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match = iterator.previous();
                if (focusEarlier)
                {
                    if (match.getSecond() != null)
                        match.getSecond().focus(Focus.RIGHT);
                    else
                        match.getFirst().focus(Focus.RIGHT);
                    return;
                }
                if (match.getSecond() == child)
                {
                    match.getFirst().focus(Focus.RIGHT);
                    return;
                }
                if (match.getSecond() == child)
                {
                    focusEarlier = true;
                }
            }
            if (focusEarlier)
                parent.focusLeftOf(this);
        }
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return parent.getParentStyles();
        //return Stream.concat(parent.getParentStyles(), Stream.of("match"));
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        // We don't actually want to allow general dropping to the left of us, because the
        // only thing that fits there is a case.  If we want to enable case dropping
        // we'll (a) need to remove ConsecutiveChild constraint (we're not one) and
        // (b) have a way to determine if the thing being dropped actually fits here.
        return //Stream.<Pair<ConsecutiveChild, Double>>concat(
            //Stream.of(new Pair<>(this, FXUtility.distanceToLeft(caseLabel, loc))),
            matches.stream().flatMap((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) ->
            {
                @Nullable Pair<ConsecutiveChild<? extends C>, Double> firstDrop = p.getFirst().findClosestDrop(loc, forType);
                return p.getSecond() == null ? Utility.streamNullable(firstDrop) : Utility.streamNullable(firstDrop, p.getSecond().findClosestDrop(loc, forType));
            })
            //)
            .min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @Override
    public void focusWhenShown()
    {
        matches.get(0).getFirst().focus(Focus.LEFT);
    }

    public boolean isMatchNode(ConsecutiveBase consecutive)
    {
        for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
        {
            if (match.getFirst() == consecutive)
                return true;
        }
        return false;
    }

    public Function<MatchExpression, MatchClause> toClauseExpression(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError)
    {
        List<Function<MatchExpression, Pattern>> patterns = new ArrayList<>();
        for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
        {
            Expression patExp = match.getFirst().save(errorDisplayer, onError);
            @Nullable Expression matchExp = match.getSecond() == null ? null : match.getSecond().save(errorDisplayer, onError);
            patterns.add(me -> new Pattern(patExp, matchExp));
        }
        Expression outcomeExp = this.outcome.save(errorDisplayer, onError);
        return me -> me.new MatchClause(Utility.<Function<MatchExpression, Pattern>, Pattern>mapList(patterns, f -> f.apply(me)), outcomeExp);
    }

    public void setSelected(boolean selected)
    {
        for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
        {
            match.getFirst().setSelected(selected);
            if (match.getSecond() != null)
                match.getSecond().setSelected(selected);
        }
        outcome.setSelected(selected);
    }

    public void focusChanged()
    {
        for (Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> match : matches)
        {
            match.getFirst().focusChanged();
            if (match.getSecond() != null)
                match.getSecond().focusChanged();
        }
        outcome.focusChanged();
    }

    public boolean isFocused()
    {
        return outcome.childIsFocused() || matches.stream().anyMatch((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> m) -> m.getFirst().childIsFocused() || (m.getSecond() != null && m.getSecond().childIsFocused()));
    }

    @Override
    public ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    {
        return ImmutableList.of(new Pair<>("or", "op.caseor"), new Pair<>("given", "op.casegiven"));
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode child)
    {
        // Only declare variables in a pattern, not guard or outcome:
        return matches.stream().anyMatch((Pair<ConsecutiveBase<Expression, ExpressionNodeParent>, @Nullable ConsecutiveBase<Expression, ExpressionNodeParent>> p) -> p.getFirst() == child);
    }
}

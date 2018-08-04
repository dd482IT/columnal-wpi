package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.expressioneditor.UnitSaver.Context;
import records.transformations.expression.*;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class UnitSaver extends SaverBase<UnitExpression, UnitSaver, UnitOp, UnitBracket, Context>// implements ErrorAndTypeRecorder
{
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(
            opD(UnitOp.MULTIPLY, "op.times")), UnitSaver::makeTimes),
        new OperatorExpressionInfo(
            opD(UnitOp.DIVIDE, "op.divide"), UnitSaver::makeDivide),
        new OperatorExpressionInfo(
            opD(UnitOp.RAISE, "op.raise"), UnitSaver::makeRaise));

    public UnitSaver(ConsecutiveBase<UnitExpression, UnitSaver> parent)
    {
        super(parent);
    }
    
    private UnitSaver()
    {
    }
    
    private static UnitExpression makeTimes(ImmutableList<@Recorded UnitExpression> expressions, List<UnitOp> operators, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(UnitExpression lhs, UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(UnitExpression lhs, UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver> bracketedStatus)
    {
        if (rhs instanceof UnitExpressionIntLiteral)
            return new UnitRaiseExpression(lhs, ((UnitExpressionIntLiteral) rhs).getNumber());
        else
            return new InvalidOperatorUnitExpression(ImmutableList.of(
                    lhs, new InvalidSingleUnitExpression("^"), rhs
            ));
    };

    //UnitManager getUnitManager();

    class Context {}
    
    @Override
    protected @Recorded UnitExpression makeExpression(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, List<Either<@Recorded UnitExpression, OpAndNode>> content, BracketAndNodes<UnitExpression, UnitSaver> brackets)
    {
        if (content.isEmpty())
            return new InvalidOperatorUnitExpression(ImmutableList.of());

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded UnitExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<UnitOp> validOperators = collectedItems.getValidOperators();
            
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);

            // Raise is a special case as it doesn't need to be bracketed:
            for (int i = 0; i < validOperators.size(); i++)
            {
                if (validOperators.get(i).equals(UnitOp.RAISE))
                {
                    if (validOperands.get(i) instanceof SingleUnitExpression && validOperands.get(i + 1) instanceof UnitExpressionIntLiteral)
                    {
                        validOperators.remove(i);
                        UnitExpressionIntLiteral power = (UnitExpressionIntLiteral) validOperands.remove(i + 1);
                        validOperands.set(i, new UnitRaiseExpression(validOperands.get(i), power.getNumber()));
                    }
                }
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable UnitExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), errorDisplayerRecord, arg ->
                    makeInvalidOp(brackets.start, brackets.end, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets, arg -> arg);
            if (e != null)
            {
                return e;
            }

        }

        return new InvalidOperatorUnitExpression(Utility.mapListI(collectedItems.getInvalid(), e -> e.either(o -> new InvalidSingleUnitExpression(o.getContent()), x -> x)));
    }

    @Override
    protected UnitExpression makeSingleInvalid(UnitBracket terminator)
    {
        return new InvalidSingleUnitExpression(terminator.getContent());
    }

    @Override
    protected @Recorded UnitExpression makeInvalidOp(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, ImmutableList<Either<UnitOp, @Recorded UnitExpression>> items)
    {
        return errorDisplayerRecord.recordUnit(start, end, new InvalidOperatorUnitExpression(Utility.mapListI(items, x -> x.either(op -> new InvalidSingleUnitExpression(op.getContent()), y -> y))));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    public void saveBracket(UnitBracket bracket, ConsecutiveChild<UnitExpression, UnitSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, new Terminator()
            {
                @Override
                public void terminate(FetchContent<UnitExpression, UnitSaver> makeContent, @Nullable UnitBracket terminator, ConsecutiveChild<UnitExpression, UnitSaver> keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
                {
                    BracketAndNodes<UnitExpression, UnitSaver> brackets = new BracketAndNodes<>(BracketedStatus.DIRECT_ROUND_BRACKETED, errorDisplayer, keywordErrorDisplayer);
                    if (terminator == UnitBracket.CLOSE_ROUND)
                    {
                        // All is well:
                        @Recorded UnitExpression result = makeContent.fetchContent(brackets);
                        currentScopes.peek().items.add(Either.left(result));
                    } 
                    else
                    {
                        // Error!
                        keywordErrorDisplayer.addErrorAndFixes(StyledString.s("Expected ) but found " + terminator), ImmutableList.of());
                        // Important to call makeContent before adding to scope on the next line:
                        ImmutableList.Builder<UnitExpression> items = ImmutableList.builder();
                        items.add(new InvalidSingleUnitExpression(bracket.getContent()));
                        items.add(makeContent.fetchContent(brackets));
                        if (terminator != null)
                            items.add(new InvalidSingleUnitExpression(terminator.getContent()));
                        @Recorded UnitExpression invalid = record(brackets.start, keywordErrorDisplayer, new InvalidOperatorUnitExpression(items.build()));
                        currentScopes.peek().items.add(Either.left(invalid));
                    }
                }
            }));
        }
        else
        {
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope(errorDisplayer.getParent());
            }
            cur.terminator.terminate((BracketAndNodes<UnitExpression, UnitSaver> brackets) -> makeExpression(brackets.start, brackets.end, cur.items, brackets), bracket, errorDisplayer, withContext);
        }
    }

    @Override
    protected UnitExpression keywordToInvalid(UnitBracket unitBracket)
    {
        return new InvalidSingleUnitExpression(unitBracket.getContent());
    }

    @Override
    protected @Recorded UnitExpression record(ConsecutiveChild<UnitExpression, UnitSaver> start, ConsecutiveChild<UnitExpression, UnitSaver> end, UnitExpression unitExpression)
    {
        return errorDisplayerRecord.recordUnit(start, end, unitExpression);
    }

    public static ImmutableList<OperatorExpressionInfo> getOperators()
    {
        return new UnitSaver().OPERATORS;
    }
}

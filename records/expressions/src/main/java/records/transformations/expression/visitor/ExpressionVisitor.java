package records.transformations.expression.visitor;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.TableId;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager.TagInfo;
import records.transformations.expression.AddSubtractExpression;
import records.transformations.expression.AddSubtractExpression.AddSubtractOp;
import records.transformations.expression.AndExpression;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ComparisonExpression;
import records.transformations.expression.ComparisonExpression.ComparisonOperator;
import records.transformations.expression.ConstructorExpression;
import records.transformations.expression.DivideExpression;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.ImplicitLambdaArg;
import records.transformations.expression.InvalidIdentExpression;
import records.transformations.expression.InvalidOperatorExpression;
import records.transformations.expression.MatchAnythingExpression;
import records.transformations.expression.MatchExpression;
import records.transformations.expression.MatchExpression.MatchClause;
import records.transformations.expression.NotEqualExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.OrExpression;
import records.transformations.expression.PlusMinusPatternExpression;
import records.transformations.expression.RaiseExpression;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.StringConcatExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TemporalLiteral;
import records.transformations.expression.TimesExpression;
import records.transformations.expression.TupleExpression;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitLiteralExpression;
import records.transformations.expression.VarDeclExpression;
import records.transformations.expression.function.StandardFunctionDefinition;
import records.transformations.expression.type.TypeExpression;
import styled.StyledString;
import utility.Either;

import java.time.temporal.TemporalAccessor;
import java.util.List;

public interface ExpressionVisitor<T>
{
    T notEqual(NotEqualExpression self, @Recorded Expression lhs, @Recorded Expression rhs);
    T divide(DivideExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T addSubtract(AddSubtractExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<AddSubtractOp> ops);

    T and(AndExpression self, ImmutableList<@Recorded Expression> expressions);
    T or(OrExpression self, ImmutableList<@Recorded Expression> expressions);

    T list(ArrayExpression self, ImmutableList<@Recorded Expression> items);

    T column(ColumnReference self, @Nullable TableId tableName, ColumnId columnName, ColumnReferenceType referenceType);

    T litBoolean(BooleanLiteral self, @Value Boolean value);

    T call(CallExpression self, @Recorded Expression callTarget, ImmutableList<@Recorded Expression> arguments);

    T comparison(ComparisonExpression self, ImmutableList<@Recorded Expression> expressions, ImmutableList<ComparisonOperator> operators);
    // Singular name to avoid clash with Object.equals
    T equal(EqualExpression self, ImmutableList<@Recorded Expression> expressions);

    T ident(IdentExpression self, @ExpressionIdentifier String text);

    T ifThenElse(IfThenElseExpression self, @Recorded Expression condition, @Recorded Expression thenExpression, @Recorded Expression elseExpression);

    T invalidIdent(InvalidIdentExpression self, String text);

    T implicitLambdaArg(ImplicitLambdaArg self);

    T invalidOps(InvalidOperatorExpression self, ImmutableList<@Recorded Expression> items);

    T matchAnything(MatchAnythingExpression self);

    T litNumber(NumericLiteral self, @Value Number value, @Nullable UnitExpression unit);

    T plusMinus(PlusMinusPatternExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T raise(RaiseExpression self, @Recorded Expression lhs, @Recorded Expression rhs);

    T standardFunction(StandardFunction self, StandardFunctionDefinition functionDefinition);

    T concatText(StringConcatExpression self, ImmutableList<@Recorded Expression> expressions);

    T litText(StringLiteral self, @Value String value);

    T litTemporal(TemporalLiteral self, DateTimeType literalType, String content, Either<StyledString, TemporalAccessor> value);

    T multiply(TimesExpression self, ImmutableList<@Recorded Expression> expressions);

    T tuple(TupleExpression self, ImmutableList<@Recorded Expression> members);

    T litType(TypeLiteralExpression self, TypeExpression type);

    T litUnit(UnitLiteralExpression self, @Recorded UnitExpression unitExpression);

    T varDecl(VarDeclExpression self, @ExpressionIdentifier String varName);

    T constructor(ConstructorExpression self, Either<String, TagInfo> tag);

    T match(MatchExpression self, @Recorded Expression expression, ImmutableList<MatchClause> clauses);
}
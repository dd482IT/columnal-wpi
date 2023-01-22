/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.transformations.expression.Expression.CheckedExp;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.typeExp.TypeConcretisationError;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.TypeExp.TypeError;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;

import java.util.List;
import java.util.function.Consumer;

/**
 * A listener that records where errors have occurred when checking an expression.
 * 
 * Note: it is valid to record an error and/or quick fixes multiple times
 * for the same expression.  In this case, the error and list of quick fixes
 * should be concatenated to form the final outcome.
 */
public interface ErrorAndTypeRecorder
{
    /**
     * Checks the given error-or-type.  If error, that error is recorded 
     * (with no available quick fixes) and null is returned.
     * If type, it is returned directly.
     * @param src
     * @param errorOrType
     * @return
     */
    public default TypeExp recordError(Expression src, Either<TypeError, TypeExp> errorOrType)
    {
        return errorOrType.<TypeExp>either(err -> {
            if (err != null)
            {
                recordError(src, err.getMessage());
            }
            return null;
        }, val -> val);
    }

    /**
     * Records an error source and error message
     */
    public default <T> T recordLeftError(TypeManager typeManager, FunctionLookup functionLookup, Expression src, Either<TypeConcretisationError, T> errorOrVal)
    {
        return errorOrVal.<T>either(err -> {
            DataType fix = err.getSuggestedTypeFix();
            recordError(src, err.getErrorText());
            recordQuickFixes(src, ExpressionUtil.quickFixesForTypeError(typeManager, functionLookup, src, fix));
            return null;
        }, val -> val);
    }

    /**
     * Records the type for a particular expression.
     */
    public default TypeExp recordType(Expression expression, TypeExp typeExp)
    {
        if (typeExp == null)
            return null;
        else
            return recordTypeNN(expression, typeExp);
    }

    public default CheckedExp recordType(Expression expression, TypeState typeState, TypeExp typeExp)
    {
        if (typeExp != null)
            return new CheckedExp(recordTypeNN(expression, typeExp), typeState);
        else
            return null;
    }

    /**
     * A curried version of the two-arg function of the same name.
     */
    public default Consumer<StyledString> recordErrorCurried(Expression src)
    {
        return errMsg -> recordError(src, errMsg);
    }

    /**
     * Records an error source and error message.
     */
    public <EXPRESSION> void recordError(EXPRESSION src, StyledString error);

    /**
     * Records an source and information message.
     */
    public <EXPRESSION extends StyledShowable> void recordInformation(EXPRESSION src, Pair<StyledString, QuickFix<EXPRESSION>> informaton);
    
    public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> fixes);

    public default CheckedExp recordTypeAndError(Expression expression, Either<TypeError, TypeExp> typeOrError, TypeState typeState)
    {
        return recordTypeAndError(expression, expression, typeOrError, typeState);
    }
    
    public default CheckedExp recordTypeAndError(Expression typeExpression, Expression errorExpression, Either<TypeError, TypeExp> typeOrError, TypeState typeState)
    {
        TypeExp typeExp = recordType(typeExpression, recordError(errorExpression, typeOrError));
        if (typeExp == null)
            return null;
        else
            return new CheckedExp(typeExp, typeState);
    }

    public TypeExp recordTypeNN(Expression expression, TypeExp typeExp);

}

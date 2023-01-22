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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.datatype.TypeManager.TagInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.transformations.expression.Expression.ColumnLookup.FoundTable;
import xyz.columnal.transformations.expression.explanation.Explanation.ExecutionType;
import xyz.columnal.transformations.expression.explanation.ExplanationLocation;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.typeExp.MutVar;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.MutUnitVar;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A plain identifier.  If it resolves to a variable, it's a variable-use.  If not, it's an unresolved identifier.
 * 
 * IdentExpression differs from InvalidIdentExpression
 * in that IdentExpression is always *syntactically* valid,
 * whereas InvalidIdentExpression is always syntactically
 * *invalid*, because it does not parse as an ident
 * and given its position, it cannot be treated as an
 * operator part of the expression (e.g. a trailing +
 * with no following operand)
 */
public class IdentExpression extends NonOperatorExpression
{
    // The name of the field in a table reference with the list of rows
    public static final String ROWS = "rows";
    public static final String NAMESPACE_COLUMN = "column";

    // TODO add resolver listener
    private final String namespace;
    private final ImmutableList<String> idents;
    private Resolution resolution;

    private IdentExpression(String namespace, ImmutableList<String> idents)
    {
        this.namespace = namespace;
        this.idents = idents;
    }

    public static IdentExpression load(String namespace, ImmutableList<String> idents)
    {
        return new IdentExpression(namespace, idents);
    }

    public static IdentExpression load(String ident)
    {
        return new IdentExpression(null, ImmutableList.<String>of(ident));
    }

    public static IdentExpression tag(String typeName, String tagName)
    {
        return new IdentExpression("tag", ImmutableList.<String>of(typeName, tagName));
    }
    
    public static IdentExpression tag(String tagName)
    {
        return new IdentExpression("tag", ImmutableList.<String>of(tagName));
    }

    public static IdentExpression function(ImmutableList<String> functionFullName)
    {
        return new IdentExpression("function", functionFullName);
    }

    public static IdentExpression table(String tableName)
    {
        return new IdentExpression("table", ImmutableList.<String>of(tableName));
    }

    public static IdentExpression column(TableId tableName, ColumnId columnName)
    {
        if (tableName == null)
            return column(columnName);
        else
            return new IdentExpression(NAMESPACE_COLUMN, ImmutableList.<String>of(tableName.getRaw(), columnName.getRaw()));
    }

    public static IdentExpression column(ColumnId columnName)
    {
        return new IdentExpression(NAMESPACE_COLUMN, ImmutableList.<String>of(columnName.getRaw()));
    }

    @SuppressWarnings("recorded") // Only used for items which will be reloaded anyway
    public static Expression makeEntireColumnReference(TableId tableId, ColumnId columnId)
    {
        return new FieldAccessExpression(IdentExpression.table(tableId.getRaw()), columnId.getRaw());
    }

    // Resolves all IdentExpression recursively throughout the
    // whole expression:
    public static void resolveThroughout(Expression expression, ColumnLookup columnLookup, FunctionLookup functionLookup, TypeManager typeManager) throws InternalException, UserException
    {
        Either<InternalException, UserException> ex = expression.visit(new ExpressionVisitorStream<Either<InternalException, UserException>>() {
            @Override
            public Stream<Either<InternalException, UserException>> ident(IdentExpression self, String namespace, ImmutableList<String> idents, boolean isVariable)
            {
                try
                {
                    Resolution res = self.resolve(columnLookup, functionLookup, typeManager);
                    if (res != null)
                        self.resolution = res;
                    return super.ident(self, namespace, idents, isVariable);
                }
                catch (InternalException e)
                {
                    return Stream.<Either<InternalException, UserException>>of(Either.<InternalException, UserException>left(e));
                }
                catch (UserException e)
                {
                    return Stream.<Either<InternalException, UserException>>of(Either.<InternalException, UserException>right(e));
                }
            }
        }).findFirst().orElse(null);
        if (ex != null)
            ex.eitherEx_(e -> {throw e;}, e -> {throw e;});
    }


    @Override
    public CheckedExp check(IdentExpression this, ColumnLookup dataLookup, TypeState original, ExpressionKind kind, LocationInfo locationInfo, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        // I think should now be impossible:
        String invalid = streamAllParts().filter(n -> !GrammarUtility.validIdentifier(n)).findFirst().orElse(null);
        if (invalid != null)
        {
            onError.recordError(this, StyledString.s("Invalid identifier: \"" + invalid + "\""));
            return null;
        }
        
        if (resolution == null)
        {
            // Didn't find it anywhere:
            onError.recordError(this, StyledString.s("Unknown name: \"" + idents.stream().collect(Collectors.joining("\\")) + "\""));
            QuickFix<Expression> fix = dataLookup.getFixForIdent(namespace, idents, this);
            if (fix != null)
            {
                onError.recordQuickFixes(this, ImmutableList.<QuickFix<Expression>>of(fix));
            }
            return null;
        }
        else
            return resolution.checkType(this, original, kind, onError);
    }
    
    private Resolution resolve(IdentExpression this, ColumnLookup dataLookup, FunctionLookup functionLookup, TypeManager typeManager) throws InternalException, UserException
    {
        // Possible lookup destinations, in order of preference:
        // (Preference is roughly how likely it is that the user defined it and wants it)
        // - Local variable (cannot be scoped)
        // - Column name (Scope: "column")
        // - Table name (Scope: "table")
        // - Tag name (Scope: "tag")
        // - Standard function name (Scope: "function")

        if (namespace == null || namespace.equals(NAMESPACE_COLUMN))
        {
            Expression.ColumnLookup.FoundColumn col;
            final ColumnId columnName;
            if (idents.size() == 1)
            {
                columnName = new ColumnId(idents.get(0));
                col = dataLookup.getColumn(this, null, columnName);
            }
            else
            {
                columnName = new ColumnId(idents.get(1));
                col = dataLookup.getColumn(this, new TableId(idents.get(0)), columnName);
            }
            if (col == null && Objects.equals(namespace, NAMESPACE_COLUMN))
            {
                //onError.recordError(this, StyledString.s("Could not find source column " + toText(namespace, idents)));
                return null;
            }
            else if (col != null)
            {
                TableId resolvedTableName = col.tableCanBeOmitted ? null : col.tableId;
                DataTypeValue column = col.dataTypeValue;
                return new ColumnResolution(column, col, columnName, resolvedTableName);
            }
        }
        if (namespace == null || namespace.equals("table"))
        {
            FoundTable table = dataLookup.getTable(new TableId(idents.get(0)));
            if (table == null && Objects.equals(namespace, "table"))
            {
                return null;
            }
            else if (table != null)
            {
                HashMap<String, TypeExp> fieldsAsSingle = new HashMap<>();
                HashMap<String, TypeExp> fieldsAsList = new HashMap<>();

                for (Entry<ColumnId, DataTypeValue> entry : table.getColumnTypes().entrySet())
                {
                    fieldsAsList.put(entry.getKey().getRaw(), TypeExp.list(this, TypeExp.fromDataType(this, entry.getValue().getType())));
                    fieldsAsSingle.put(entry.getKey().getRaw(), TypeExp.fromDataType(this, entry.getValue().getType()));
                }

                boolean includeRows;
                if (!fieldsAsList.containsKey(ROWS))
                {
                    includeRows = true;
                    fieldsAsList.put(ROWS, TypeExp.list(this, TypeExp.record(this, fieldsAsSingle, true)));
                }
                else
                    includeRows = false;
                FoundTable resolvedTable = table;
                return new TableResolution(resolvedTable, includeRows, fieldsAsList);
            }
        }
        if (namespace == null || namespace.equals("tag"))
        {
            TagInfo tag = null;
            switch (idents.size())
            {
                case 1:
                    tag = typeManager.lookupTag(null, idents.get(0)).leftToNull();
                    break;
                case 2:
                    tag = typeManager.lookupTag(idents.get(0), idents.get(1)).leftToNull();
                    break;
                default:
                    if (Objects.equals(namespace, "tag"))
                    {
                        //onError.recordError(this, StyledString.s("Found " + idents.size() + " identifiers but tags should either have one (tag name) or two (type name, tag name)"));
                        return null;
                    }
                    break;
            }
            if (tag != null)
            {
                TagInfo tagFinal = tag;
                return new TagResolution(tagFinal);
            }
        }
        if (namespace == null || namespace.equals("function"))
        {
            StandardFunctionDefinition functionDefinition = functionLookup.lookup(idents.stream().collect(Collectors.joining("\\")));
            if (functionDefinition != null)
            {
                Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type = functionDefinition.getType(typeManager);
                return new FunctionResolution(functionDefinition, type);
            }
        }

        if ((namespace == null || namespace.equals("var")) && idents.size() == 1)
        {
            return new VariableResolution(Objects.equals(namespace, "var"));
        }


        if (namespace != null && !ImmutableList.of(NAMESPACE_COLUMN, "table", "tag", "function", "var").contains(namespace))
        {
            // TODO give more specific error during checkType:
            //onError.recordError(this, StyledString.s("Unknown namespace or identifier: \"" + namespace + "\".  Known namespaces: column, table, tag, function."));
            return null;
        }
        return null;
    }

    private Stream<String> streamAllParts()
    {
        return Stream.<String>concat(Utility.<String>streamNullable(namespace), idents.stream());
    }

    private TypeExp makeTagType(TagInfo t) throws InternalException
    {
        Pair<TypeExp, ImmutableList<TypeExp>> taggedType = TypeExp.fromTagged(this, t.wholeType);
        return taggedType.getSecond().get(t.tagIndex);
    }

    @Override
    public ValueResult matchAsPattern(Object value, EvaluateState state) throws InternalException, EvaluationException
    {
        if (resolution == null)
            throw new InternalException("Calling matchAsPattern on variable without typecheck");
        else if (resolution.isDeclarationInMatch())
            return explanation(DataTypeUtility.value(true), ExecutionType.MATCH, state.add(idents.get(0), value), ImmutableList.of(), ImmutableList.of(), false);
        else
            return super.matchAsPattern(value, state);
    }

    @Override
    public ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        if (resolution == null)
            throw new InternalException("Calling getValue on variable without typecheck");
        else
        {
            try
            {
                return resolution.getValue(state);
            }
            catch (UserException e)
            {
                throw new EvaluationException(e, IdentExpression.this, ExecutionType.VALUE, state, ImmutableList.of());
            }
        }
    }

    @Override
    public boolean hideFromExplanation(boolean skipIfTrivial)
    {
        if (resolution != null)
            return resolution.hideFromExplanation(skipIfTrivial);
        else
            return super.hideFromExplanation(skipIfTrivial);
    }

    @Override
    public String save(SaveDestination saveDestination, BracketedStatus surround, TableAndColumnRenames renames)
    {
        return toText(resolution == null ? saveDestination.disambiguate(namespace, idents) : resolution.save(saveDestination, renames));
    }

    private static String toText(Pair<String, ImmutableList<String>> namespaceAndIdents)
    {
        return toText(namespaceAndIdents.getFirst(), namespaceAndIdents.getSecond());
    }
    
    private static String toText(String namespace, ImmutableList<String> idents)
    {
        StringBuilder stringBuilder = new StringBuilder();
        if (namespace != null)
            stringBuilder.append(namespace).append("\\\\");
        for (int i = 0; i < idents.size(); i++)
        {
            if (i > 0)
                stringBuilder.append("\\");
            stringBuilder.append(idents.get(i));
        }
        return stringBuilder.toString();
    }

    @Override
    protected StyledString toDisplay(DisplayType displayType, BracketedStatus bracketedStatus, ExpressionStyler expressionStyler)
    {
        return expressionStyler.styleExpression(StyledString.s(displayType == DisplayType.SIMPLE ? idents.get(idents.size() - 1) : toText(namespace, idents)), this);
    }

    @Override
    public Stream<Pair<Expression, Function<Expression, Expression>>> _test_childMutationPoints()
    {
        return Stream.empty();
    }

    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws InternalException, UserException
    {
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentExpression that = (IdentExpression) o;
        return Objects.equals(toString(), that.toString());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(toString());
    }

    @Override
    public Expression replaceSubExpression(Expression toReplace, Expression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public <T> T visit(IdentExpression this, ExpressionVisitor<T> visitor)
    {
        return visitor.ident(this, namespace, idents, resolution != null && resolution.isVariable());
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     */
    public boolean isVarDeclaration()
    {
        return resolution != null && resolution.isDeclarationInMatch();
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     * Bit hacky to provide this, but useful elsewhere...
     */
    public TagInfo getResolvedConstructor()
    {
        return resolution != null ? resolution.getResolvedConstructor() : null;
    }

    /**
     * Only valid to call after type-checking!  Before that we can't know.
     * Bit hacky to provide this, but useful elsewhere...
     */
    public StandardFunctionDefinition getFunctionDefinition()
    {
        return resolution != null ? resolution.getResolvedFunctionDefinition() : null;
    }

    private static interface Resolution
    {
        public default boolean isDeclarationInMatch()
        {
            return false;
        }
        
        public boolean hideFromExplanation(boolean skipIfTrivial);

        public ValueResult getValue(EvaluateState state) throws InternalException, UserException;
        
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames);

        public boolean isVariable();
        
        public default TagInfo getResolvedConstructor()
        {
            return null;
        }

        public default StandardFunctionDefinition getResolvedFunctionDefinition()
        {
            return null;
        }

        public CheckedExp checkType(IdentExpression identExpression, TypeState state, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws InternalException;
    }
    
    // If parameter is IdentExpression with single ident and no namespace, return the ident
    public static String getSingleIdent(Expression expression)
    {
        if (expression instanceof IdentExpression)
        {
            IdentExpression identExpression = (IdentExpression) expression;
            if (identExpression.namespace == null && identExpression.idents.size() == 1)
                return identExpression.idents.get(0);
        }
        return null;
    }

    private class ColumnResolution implements Resolution
    {
        private final DataTypeValue column;
        private final Expression.ColumnLookup.FoundColumn col;
        private final ColumnId columnName;
        private final TableId resolvedTableName;

        public ColumnResolution(DataTypeValue column, Expression.ColumnLookup.FoundColumn col, ColumnId columnName, TableId resolvedTableName)
        {
            this.column = column;
            this.col = col;
            this.columnName = columnName;
            this.resolvedTableName = resolvedTableName;
        }

        @Override
        public boolean hideFromExplanation(boolean skipIfTrivial)
        {
            return skipIfTrivial;
        }

        @Override
        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
        {
            if (column == null)
                throw new InternalException("Attempting to fetch value despite type check failure");
            ExplanationLocation explanationLocation = new ExplanationLocation(col.tableId, columnName, state.getRowIndex());
            return resultIsLocation(column.getCollapsed(state.getRowIndex()), state, ImmutableList.of(), explanationLocation, false);
        }

        @Override
        public boolean isVariable()
        {
            return false;
        }

        private String getFoundNamespace()
        {
            return NAMESPACE_COLUMN;
        }

        private ImmutableList<String> getFoundFullName()
        {
            return ImmutableList.of(col.tableId.getRaw(), columnName.getRaw());
        }

        @Override
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            final Pair<TableId, ColumnId> renamed = renames.columnId(resolvedTableName, columnName, null);

            final TableId renamedTableId = renamed.getFirst();
            ImmutableList<String> tablePlusColumn = renamedTableId != null ? ImmutableList.of(renamedTableId.getRaw(), renamed.getSecond().getRaw()) : ImmutableList.of(renamed.getSecond().getRaw());

            return saveDestination.disambiguate(getFoundNamespace(), tablePlusColumn);
        }

        @Override
        public CheckedExp checkType(IdentExpression identExpression, TypeState state, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws InternalException
        {
            if (col.information != null)
            {
                onError.recordInformation(identExpression, col.information);
            }
            return onError.recordType(identExpression, state, TypeExp.fromDataType(IdentExpression.this, column.getType()));
        }
    }

    private class TableResolution implements Resolution
    {
        private final FoundTable resolvedTable;
        private final boolean includeRows;
        private final HashMap<String, TypeExp> fieldsAsList;

        public TableResolution(FoundTable resolvedTable, boolean includeRows, HashMap<String, TypeExp> fieldsAsList)
        {
            this.resolvedTable = resolvedTable;
            this.includeRows = includeRows;
            this.fieldsAsList = fieldsAsList;
        }

        private String getFoundNamespace()
        {
            return "table";
        }

        @Override
        public boolean hideFromExplanation(boolean skipIfTrivial)
        {
            // We never want to see a full table in an explanation
            return true;
        }

        private ImmutableList<String> getFoundFullName()
        {
            return ImmutableList.of(resolvedTable.getTableId().getRaw());
        }

        @Override
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return saveDestination.disambiguate(getFoundNamespace(), ImmutableList.of(renames.tableId(new TableId(idents.get(0))).getRaw()));
        }

        @Override
        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
        {
            if (resolvedTable == null)
                throw new InternalException("Attempting to fetch value despite type check failure");
            final FoundTable resolvedTableNN = resolvedTable;
            final ImmutableMap<ColumnId, DataTypeValue> columnTypes = resolvedTableNN.getColumnTypes();
            Record result = DataTypeUtility.value(new Record()
            {
                @Override
                public Object getField(String name) throws InternalException
                {
                    if (includeRows && name.equals(ROWS))
                        return DataTypeUtility.value(new RowsAsList());
                    return DataTypeUtility.value(new ColumnAsList(Utility.getOrThrow(columnTypes, new ColumnId(name), () -> new InternalException("Cannot find column " + name))));
                }

                class RowsAsList extends ListEx
                {
                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return resolvedTableNN.getRowCount();
                    }

                    @Override
                    public Object get(int index) throws InternalException, UserException
                    {
                        ImmutableMap.Builder<String, Object> rowValuesBuilder = ImmutableMap.builder();
                        for (Entry<ColumnId, DataTypeValue> entry : columnTypes.entrySet())
                        {
                            rowValuesBuilder.put(entry.getKey().getRaw(), entry.getValue().getCollapsed(index));
                        }
                        ImmutableMap<String, Object> rowValues = rowValuesBuilder.build();

                        return DataTypeUtility.value(new Record()
                        {
                            @Override
                            public Object getField(String name) throws InternalException
                            {
                                return Utility.getOrThrow(rowValues, name, () -> new InternalException("Cannot find column " + name));
                            }

                            @Override
                            public ImmutableMap<String, Object> getFullContent() throws InternalException
                            {
                                return rowValues;
                            }
                        });
                    }
                }

                class ColumnAsList extends ListEx
                {
                    private final DataTypeValue dataTypeValue;

                    ColumnAsList(DataTypeValue dataTypeValue)
                    {
                        this.dataTypeValue = dataTypeValue;
                    }

                    @Override
                    public int size() throws InternalException, UserException
                    {
                        return resolvedTableNN.getRowCount();
                    }

                    @Override
                    public Object get(int index) throws InternalException, UserException
                    {
                        return dataTypeValue.getCollapsed(index);
                    }
                }

                @Override
                public ImmutableMap<String, Object> getFullContent() throws InternalException
                {
                    return columnTypes.entrySet().stream().collect(ImmutableMap.<Entry<ColumnId, DataTypeValue>, String, Object>toImmutableMap(e -> e.getKey().getRaw(), e -> DataTypeUtility.value(new ColumnAsList(e.getValue()))));
                }
            });

            ExplanationLocation loc = new ExplanationLocation(new TableId(idents.get(0)));
            return resultIsLocation(result, state, ImmutableList.of(), loc, true);
        }

        @Override
        public boolean isVariable()
        {
            return false;
        }

        @Override
        public CheckedExp checkType(IdentExpression identExpression, TypeState state, ExpressionKind expressionKind, ErrorAndTypeRecorder onError)
        {
            return new CheckedExp(onError.recordType(IdentExpression.this, TypeExp.record(IdentExpression.this, fieldsAsList, true)), state);
        }
    }

    private class TagResolution implements Resolution
    {
        private final TagInfo tagFinal;

        public TagResolution(TagInfo tagFinal)
        {
            this.tagFinal = tagFinal;
        }

        @Override
        public boolean isVariable()
        {
            return false;
        }

        private String getFoundNamespace()
        {
            return "tag";
        }

        @Override
        public boolean hideFromExplanation(boolean skipIfTrivial)
        {
            // Tags themselves are always trivial as they are literals
            return true;
        }

        private ImmutableList<String> getFoundFullName()
        {
            return ImmutableList.of(tagFinal.getTypeName().getRaw(), tagFinal.getTagInfo().getName());
        }

        @Override
        public TagInfo getResolvedConstructor()
        {
            return tagFinal;
        }

        @Override
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return saveDestination.disambiguate(getFoundNamespace(), getFoundFullName());
        }

        @Override
        public ValueResult getValue(EvaluateState state)
        {
            Object value;
            if (tagFinal.getTagInfo().getInner() == null)
                value = new TaggedValue(tagFinal.tagIndex, null, tagFinal.wholeType);
            else
                value = ValueFunction.value(new ValueFunction()
                    {
                        @Override
                        public 
                        Object _call() throws InternalException, UserException
                        {
                        return new TaggedValue(tagFinal.tagIndex, arg(0), tagFinal.wholeType);
            }
                    });
            return result(value, state, ImmutableList.of());
        }

        @Override
        public CheckedExp checkType(IdentExpression identExpression, TypeState state, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws InternalException
        {
            return onError.recordType(IdentExpression.this, state, IdentExpression.this.makeTagType(tagFinal));
        }
    }

    private class FunctionResolution implements Resolution
    {
        private final StandardFunctionDefinition functionDefinition;
        private final Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type;

        public FunctionResolution(StandardFunctionDefinition functionDefinition, Pair<TypeExp, Map<String, Either<MutUnitVar, MutVar>>> type)
        {
            this.functionDefinition = functionDefinition;
            this.type = type;
        }

        @Override
        public boolean hideFromExplanation(boolean skipIfTrivial)
        {
            // Functions can't be explained anyway:
            return true;
        }

        @Override
        public boolean isVariable()
        {
            return false;
        }

        private String getFoundNamespace()
        {
            return "function";
        }

        private ImmutableList<String> getFoundFullName()
        {
            return functionDefinition.getFullName();
        }

        @Override
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return saveDestination.disambiguate(getFoundNamespace(), getFoundFullName());
        }

        @Override
        public StandardFunctionDefinition getResolvedFunctionDefinition()
        {
            return functionDefinition;
        }

        @Override
        public ValueResult getValue(EvaluateState state) throws InternalException, UserException
        {
            return result(ValueFunction.value(functionDefinition.getInstance(state.getTypeManager(), s -> {
                Either<MutUnitVar, MutVar> typeExp = type.getSecond().get(s);
                if (typeExp == null)
                    throw new InternalException("Type " + s + " cannot be found for function " + functionDefinition.getName());
                return typeExp.<Unit, DataType>mapBothEx(u -> {
                    Unit concrete = u.toConcreteUnit();
                    if (concrete == null)
                        throw new UserException("Could not resolve unit " + s + " to a concrete unit from " + u);
                    return concrete;
                }, t -> t.toConcreteType(state.getTypeManager(), false).eitherEx(
                        l -> {
                            throw new UserException(StyledString.concat(StyledString.s("Ambiguous type for call to " + functionDefinition.getName() + " "), l.getErrorText()));
                        },
                        t2 -> t2
                ));
            })), state);
        }

        @Override
        public CheckedExp checkType(IdentExpression identExpression, TypeState state, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws InternalException
        {
            return onError.recordType(IdentExpression.this, state, type.getFirst());
        }
    }

    private class VariableResolution implements Resolution
    {
        private boolean explicitVarNamespace;
        private boolean patternMatch = false;

        public VariableResolution(boolean explicitVarNamespace)
        {
            this.explicitVarNamespace = explicitVarNamespace;
        }

        @Override
        public boolean hideFromExplanation(boolean skipIfTrivial)
        {
            return skipIfTrivial || patternMatch;
        }

        @Override
        public boolean isDeclarationInMatch()
        {
            return patternMatch;
        }

        @Override
        public boolean isVariable()
        {
            return true;
        }

        private String getFoundNamespace()
        {
            return explicitVarNamespace ? "var" : null;
        }

        private ImmutableList<String> getFoundFullName()
        {
            return idents;
        }

        @Override
        public ValueResult getValue(EvaluateState state) throws InternalException
        {
            if (patternMatch)
                throw new InternalException("Calling getValue on variable declaration (should only call matchAsPattern)");
            return result(state.get(idents.get(0)), state);
        }

        @Override
        public Pair<String, ImmutableList<String>> save(SaveDestination saveDestination, TableAndColumnRenames renames)
        {
            return saveDestination.disambiguate(getFoundNamespace(), getFoundFullName());
        }

        @Override
        public CheckedExp checkType(IdentExpression identExpression, TypeState original, ExpressionKind expressionKind, ErrorAndTypeRecorder onError) throws InternalException
        {
            List<TypeExp> varType = original.findVarType(idents.get(0));
            patternMatch = varType == null && expressionKind == ExpressionKind.PATTERN;
            if (varType != null)
            {
                // Now confident it's a var:
                explicitVarNamespace = true;
                
                // If they're trying to use a variable with many types, it justifies us trying to unify all the types:
                return onError.recordTypeAndError(identExpression, TypeExp.unifyTypes(varType), original);
            }
            else if (patternMatch)
            {
                // Assume it's a var:
                explicitVarNamespace = true;
                
                MutVar patternType = new MutVar(identExpression);
                TypeState state = original.add(idents.get(0), patternType, s -> onError.recordError(this, s));
                if (state == null)
                    return null;
                return onError.recordType(identExpression, state, patternType);
            }
            else
            {
                onError.recordError(identExpression, StyledString.s("Unknown variable: \"" + idents.get(0) + "\""));
                return null;
            }
        }
    }
}

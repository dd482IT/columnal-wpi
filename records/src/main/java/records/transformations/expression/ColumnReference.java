package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 25/11/2016.
 */
public class ColumnReference extends Expression
{
    private final @Nullable TableId tableName;
    private final ColumnId columnName;
    private @MonotonicNonNull Column column;

    public ColumnReference(@Nullable TableId tableName, ColumnId columnName)
    {
        this.tableName = tableName;
        this.columnName = columnName;
    }

    public ColumnReference(ColumnId columnName)
    {
        this(null, columnName);
    }

    @Override
    public DataType check(RecordSet data, TypeState state, ExBiConsumer<Expression, String> onError) throws UserException, InternalException
    {
        column = data.getColumn(columnName);
        return column.getType();
    }

    @Override
    public List<Object> getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        if (column == null)
            throw new InternalException("Attempting to fetch value despite type check failure");
        return column.getType().getCollapsed(rowIndex);
    }

    @Override
    public @OnThread(Tag.FXPlatform) String save()
    {
        return "@" + OutputBuilder.quoted(columnName.getOutput());
    }

    @Override
    public Stream<ColumnId> allColumnNames()
    {
        return Stream.of(columnName);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException
    {
        throw new UnimplementedException();
        /*
        return check(src).apply(new DataTypeVisitor<Formula>()
        {
            @Override
            public Formula number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return formulaManager.getRationalFormulaManager().makeVariable(columnName.getOutput());
            }

            @Override
            public Formula text() throws InternalException, UserException
            {
                throw new UserException("Can't do text...");
            }

            @Override
            public Formula date() throws InternalException, UserException
            {
                throw new UserException("Can't do dates...");
            }

            @Override
            public Formula tagged(List<TagType<DataType>> tags) throws InternalException, UserException
            {
                throw new UserException("Can't do tags...");
            }

            @Override
            public Formula bool() throws InternalException, UserException
            {
                return formulaManager.getBooleanFormulaManager().makeVariable(columnName.getOutput());
            }
        });
        */
    }
}



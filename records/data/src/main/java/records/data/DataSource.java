package records.data;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.ColumnMaker;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.FormatParser;
import records.grammar.FormatParser.ColumnContext;
import records.grammar.FormatParser.ColumnNameContext;
import records.grammar.FormatParser.DefaultValueContext;
import records.grammar.FormatParser.TypeContext;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.DataFormatContext;
import records.grammar.MainParser.DataSourceContext;
import records.grammar.MainParser.DataSourceImmediateContext;
import records.grammar.MainParser.TableContext;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/11/2016.
 */
public abstract class DataSource extends Table
{
    public DataSource(TableManager mgr, @Nullable TableId id)
    {
        super(mgr, id);
    }

    public static Table loadOne(TableManager manager, String src) throws UserException, InternalException
    {
        return Utility.parseAsOne(src, MainLexer::new, MainParser::new, parser -> loadOne(manager, parser.table()));
    }

    public static Table loadOne(TableManager manager, TableContext table) throws UserException, InternalException
    {
        DataSourceContext dataSource = table.dataSource();
        if (dataSource == null)
        {
            throw new UserException("Error parsing: \"" + (table.getText().length() > 100 ? (table.getText().substring(0, 100) + "...") : table.getText()) + "\"");
        }

        if (dataSource.dataSourceImmediate() != null)
        {
            DataSourceImmediateContext immed = dataSource.dataSourceImmediate();
            List<LoadedFormat> format = loadFormat(manager.getTypeManager(), immed.dataFormat(), true);
            List<ColumnMaker<?, ?>> columns = new ArrayList<>();

            //TODO check for data row even length, error if not (allow & ignore blank lines)
            for (int i = 0; i < format.size(); i++)
            {
                DataType t = format.get(i).dataType;
                ColumnId columnId = format.get(i).columnId;
                String defaultValueUnparsed = format.get(i).defaultValueUnparsed;
                if (defaultValueUnparsed == null)
                {
                    throw new InternalException("Null default value even though we are editable; should have thrown earlier.");
                }
                columns.add(t.makeImmediateColumn(columnId, defaultValueUnparsed));
            }
            LoadedRecordSet recordSet = new LoadedRecordSet(columns, immed);
            return new ImmediateDataSource(manager, new TableId(immed.tableId().getText()), recordSet);
        }
        else
            throw new UnimplementedException();
    }

    @OnThread(Tag.Any)
    public static class LoadedFormat
    {
        public final ColumnId columnId;
        public final DataType dataType;
        public final @Nullable String defaultValueUnparsed;

        public LoadedFormat(ColumnId columnId, DataType dataType, @Nullable String defaultValueUnparsed)
        {
            this.columnId = columnId;
            this.dataType = dataType;
            this.defaultValueUnparsed = defaultValueUnparsed;
        }
    }

    @OnThread(Tag.Any)
    public static List<LoadedFormat> loadFormat(TypeManager typeManager, DataFormatContext dataFormatContext, boolean editable) throws UserException, InternalException
    {
        List<LoadedFormat> r = new ArrayList<>();
        for (TerminalNode line : dataFormatContext.detail().DETAIL_LINE())
        {
            Utility.parseAsOne(line.getText(), FormatLexer::new, FormatParser::new, p -> {
                ColumnContext column = p.column();
                ColumnNameContext colName;
                if (column == null || (colName = column.columnName()) == null)
                    throw new UserException("Problem on line " + line.getText());
                ColumnId name = new ColumnId(colName.getText());
                if (r.stream().anyMatch(pr -> pr.columnId.equals(name)))
                    throw new UserException("Duplicate column name: \"" + name + "\"");

                TypeContext type = column.type();
                if (type == null)
                    throw new UserException("Null type on line \"" + line.getText() + "\" name: " + name + " type: " + type.getText());
                DefaultValueContext defaultCtx = column.defaultValue();
                if (defaultCtx == null && editable)
                    throw new UserException("Default value missing for potentially-editable table " + name);
                DataType dataType = typeManager.loadTypeUse(type);
                r.add(new LoadedFormat(name, dataType, defaultCtx == null ? null : defaultCtx.VALUE().getText()));
                return 0;
            });
        }
        return r;
    }

    // hashCode and equals must be implemented properly (used for testing).
    // To make sure we don't forget, we introduce abstract methods which must
    // be overridden.  (We don't make hashCode and equals themselves abstract
    // because subclasses would then lose access to Table.hashCode which they'd need).
    @Override
    public final int hashCode()
    {
        return 31 * super.hashCode() + dataHashCode();
    }

    protected abstract int dataHashCode();

    @Override
    public final boolean equals(@Nullable Object obj)
    {
        if (!super.equals(obj))
            return false;
        return dataEquals((DataSource)obj);
    }

    protected abstract boolean dataEquals(DataSource obj);

    private static class LoadedRecordSet extends EditableRecordSet
    {
        public LoadedRecordSet(List<ColumnMaker<?, ?>> columns, DataSourceImmediateContext immed) throws InternalException, UserException
        {
            super(Utility.mapList(columns, c -> c::apply), () -> Utility.loadData(immed.values().detail(), p ->
            {
                for (int i = 0; i < columns.size(); i++)
                {
                    columns.get(i).loadRow(p);
                }
            }));
        }
    }
}

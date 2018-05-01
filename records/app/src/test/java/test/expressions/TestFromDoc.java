package test.expressions;

import annotation.qual.Value;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.Test;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.DataLexer;
import records.grammar.DataParser;
import records.grammar.FormatParser;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.TypeState;
import records.types.TypeConcretisationError;
import records.types.TypeExp;
import test.DummyManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestFromDoc
{
    @Test
    @OnThread(Tag.Simulation)
    public void testFromDoc() throws IOException, InternalException, UserException
    {        
        for (File file : FileUtils.listFiles(new File("target/classes"), new String[]{"test"}, false))
        {
            // Tables are scoped by file:
            Map<TableId, RecordSet> tables = new HashMap<>();
            
            List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++)
            {
                String line = lines.get(i);
                if (line.trim().isEmpty())
                    continue;

                boolean errorLine = false;
                if (line.startsWith("!!!"))
                {
                    errorLine = true;
                    line = StringUtils.removeStart(line, "!!!");
                }
                else if (line.startsWith("## "))
                {
                    String tableName = StringUtils.removeStart(lines.get(i), "##").trim();
                    String[] columnNames = StringUtils.removeStart(lines.get(++i), "##").trim().split("//");
                    String[] columnTypes = StringUtils.removeStart(lines.get(++i), "##").trim().split("//");
                    // Stored as column major:
                    List<List<String>> columnValues = Utility.replicateM(columnNames.length, ArrayList::new);
                    int length = -1;
                    i += 1;
                    while (i < lines.size()) // while (true), really -- file shouldn't end that early
                    {
                        line = lines.get(i);
                        if (line.startsWith("#### "))
                        {
                            String[] values = StringUtils.removeStart(line, "####").trim().split("//");
                            for (int j = 0; j < values.length; j++)
                            {
                                columnValues.get(j).add(values[j]); 
                            }
                            i += 1;
                        }
                        else
                        {
                            break;
                        }
                    }

                    List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
                    for (int c = 0; c < columnNames.length; c++)
                    {
                        DataType dataType = DummyManager.INSTANCE.getTypeManager().loadTypeUse(columnTypes[c]);
                        List<@Value Object> loadedValues = Utility.mapListEx(columnValues.get(c), unparsed -> {
                            return Utility.parseAsOne(unparsed, DataLexer::new, DataParser::new, p -> 
                                DataType.loadSingleItem(dataType, p, false));
                        });
                        if (length == -1)
                            length = loadedValues.size();
                        else if (length != loadedValues.size())
                            throw new InternalException("Column length mismatch in table data for " + tableName);
                            
                        columns.add(dataType.makeImmediateColumn(new ColumnId(columnNames[c]),
                            loadedValues,
                            loadedValues.get(0)    
                        ));
                    }
                    
                    tables.put(new TableId(tableName), new KnownLengthRecordSet(columns, length));
                }

                Expression expression = Expression.parse(null, line, DummyManager.INSTANCE.getTypeManager());
                ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
                TypeExp typeExp = expression.check(new TableLookup()
                {
                    @Override
                    public @Nullable RecordSet getTable(@Nullable TableId tableId)
                    {
                        return tables.get(tableId);
                    }
                }, new TypeState(DummyManager.INSTANCE.getUnitManager(), DummyManager.INSTANCE.getTypeManager()), errors);
                assertEquals("Errors for " + line, Arrays.asList(), errors.getAllErrors().collect(Collectors.toList()));
                assertNotNull(line, typeExp);
                if (typeExp == null) continue; // Won't happen
                Either<TypeConcretisationError, DataType> concreteType = typeExp.toConcreteType(DummyManager.INSTANCE.getTypeManager());
                // It may be a type concretisation error e.g. for minimum([])
                if (!errorLine)
                    assertEquals(line, Either.right(DataType.BOOLEAN), concreteType);
                if (errorLine)
                {
                    // Must be user exception
                    try
                    {
                        expression.getBoolean(0, new EvaluateState(DummyManager.INSTANCE.getTypeManager()), null);
                        Assert.fail("Expected error but got none for\n" + line);
                    }
                    catch (UserException e)
                    {
                        // As expected!
                    }
                }
                else
                {
                    boolean result = expression.getBoolean(0, new EvaluateState(DummyManager.INSTANCE.getTypeManager()), null);
                    assertTrue(line, result);
                }
            }
        }
    }
}

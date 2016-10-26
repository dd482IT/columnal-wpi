package records.data;

import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Utility;
import utility.Workers;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Created by neil on 21/10/2016.
 */
public class SummaryStatistics extends Transformation
{
    public static enum SummaryType
    {
        MEAN, MEDIAN, MIN, MAX;
    }

    private final RecordSet result;

    private static class JoinedSplit
    {
        private final List<Column> colName = new ArrayList<>();
        private final List<Object> colValue = new ArrayList<>();

        public JoinedSplit()
        {
        }

        public JoinedSplit(Column column, Object value, JoinedSplit addTo)
        {
            colName.add(column);
            colValue.add(value);
            colName.addAll(addTo.colName);
            colValue.addAll(addTo.colValue);
        }

        public boolean satisfied(int index) throws InternalException, UserException
        {
            for (int c = 0; c < colName.size(); c++)
            {
                if (!colName.get(c).get(index).equals(colValue.get(c)))
                    return false;
            }
            return true;
        }
    }

    public SummaryStatistics(RecordSet src, Map<String, Set<SummaryType>> summaries, List<String> splitBy) throws InternalException, UserException
    {
        List<JoinedSplit> splits = calcSplits(src, splitBy);

        List<Function<RecordSet, Column>> columns = new ArrayList<>();

        if (!splitBy.isEmpty())
        {
            for (int i = 0; i < splitBy.size(); i++)
            {
                String colName = splitBy.get(i);
                Column orig = src.getColumn(colName);
                int iFinal = i;
                columns.add(rs -> new Column(rs)
                {
                    @Override
                    public Object get(int index) throws UserException, InternalException
                    {
                        return splits.get(index).colValue.get(iFinal);
                    }

                    @Override
                    @OnThread(Tag.Any)
                    public String getName()
                    {
                        return colName;
                    }

                    @Override
                    public long getVersion()
                    {
                        return 1;
                    }

                    @Override
                    public Class<?> getType()
                    {
                        return orig.getType();
                    }
                });
            }
        }

        for (Entry<String, Set<SummaryType>> e : summaries.entrySet())
        {
            for (SummaryType summaryType : e.getValue())
            {
                Column srcCol = src.getColumn(e.getKey());
                boolean srcColIsNumber = Number.class.equals(srcCol.getType());
                switch (summaryType)
                {
                    case MIN:case MAX:
                        if (!Comparable.class.isAssignableFrom(srcCol.getType()) && !srcColIsNumber)
                            throw new UserException("Summary column not comparable for " + summaryType + ": " + srcCol.getType());
                        break;
                }

                columns.add(rs -> new CalculatedColumn<Object>(rs, e.getKey() + "." + summaryType, srcCol)
                {
                    @Override
                    protected boolean isSingleExpensive()
                    {
                        return true;
                    }

                    @Override
                    protected Object calculate(int index) throws UserException, InternalException
                    {
                        if (index >= splits.size())
                            throw new InternalException("Looking for item beyond end of summary");
                        JoinedSplit split = splits.get(index);
                        switch (summaryType)
                        {
                            case MIN:
                            case MAX:
                                //TODO use JFR to see what is taking so long...
                                Comparable<Object> cur = null;
                                for (int i = 0; srcCol.indexValid(i); i++)
                                {
                                    if (!split.satisfied(i))
                                        continue;

                                    Comparable<Object> x = (Comparable<Object>) srcCol.get(i);
                                    if (cur == null)
                                    {
                                        cur = x;
                                    }
                                    else
                                    {
                                        int comparison;
                                        if (srcColIsNumber)
                                            comparison = Utility.compareNumbers(cur, x);
                                        else
                                            comparison = cur.compareTo(x);
                                        if ((summaryType == SummaryType.MIN && comparison > 0)
                                            || (summaryType == SummaryType.MAX && comparison < 0))
                                            cur = x;
                                    }
                                }
                                if (cur != null)
                                    return cur;
                                else
                                    throw new UserException("Missing value");
                        }
                        throw new UserException("Unsupported summary type");
                    }

                    @Override
                    public Class<?> getType()
                    {
                        return srcCol.getType();
                    }
                });
            }
        }
        result = new RecordSet("Summary", columns) {
            @Override
            public boolean indexValid(int index) throws UserException
            {
                return index < splits.size();
            }
        };
    }

    private static class SingleSplit
    {
        private Column column;
        private List<@NonNull ?> values;

        public SingleSplit(Column column, List<@NonNull ?> values)
        {
            this.column = column;
            this.values = values;
        }
    }

    private static List<JoinedSplit> calcSplits(RecordSet src, List<String> splitBy) throws UserException, InternalException
    {
        // Each item in outer is a column.
        // Each item in inner is a possible value of that column;
        List<SingleSplit> splits = new ArrayList<>();
        for (String colName : splitBy)
        {
            Column c = src.getColumn(colName);
            Optional<List<@NonNull ?>> fastDistinct = c.fastDistinct();
            if (fastDistinct.isPresent())
                splits.add(new SingleSplit(c, fastDistinct.get()));
            else
            {
                HashSet<Object> r = new HashSet<>();
                for (int i = 0; c.indexValid(i); i++)
                {
                    r.add(c.get(i));
                }
                splits.add(new SingleSplit(c, new ArrayList(r)));
            }

        }
        // Now form cross-product:
        return crossProduct(splits, 0);
    }

    private static List<JoinedSplit> crossProduct(List<SingleSplit> allDistincts, int from)
    {
        if (from >= allDistincts.size())
            return Collections.singletonList(new JoinedSplit());
        // Take next list:
        SingleSplit cur = allDistincts.get(from);
        List<JoinedSplit> rest = crossProduct(allDistincts, from + 1);
        List<JoinedSplit> r = new ArrayList<>();
        for (Object o : cur.values)
        {
            for (JoinedSplit js : rest)
            {
                r.add(new JoinedSplit(cur.column, o, js));
            }
        }
        return r;
    }

    @OnThread(Tag.FXPlatform)
    public static void withGUICreate(RecordSet src, FXPlatformConsumer<SummaryStatistics> andThen) throws InternalException, UserException
    {
        // TODO actually show GUI
        Map<String, Set<SummaryType>> summaries = new HashMap<>();
        for (Column c : src.getColumns())
        {
            if (!c.getName().equals("Mistake"))
                summaries.put(c.getName(), new HashSet(Arrays.asList(SummaryType.MIN, SummaryType.MAX)));
        }

        Workers.onWorkerThread("Create summary statistics", () -> {
            Utility.alertOnError(() -> {
                SummaryStatistics ss = new SummaryStatistics(src, summaries, Collections.singletonList("Mistake"));
                Platform.runLater(() -> andThen.consume(ss));
                return (Void)null;
            });
        });
    }

    @Override
    @NotNull
    @OnThread(Tag.Any)
    public RecordSet getResult()
    {
        return result;
    }
}

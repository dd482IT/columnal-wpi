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

package test;

import annotation.qual.Value;
import one.util.streamex.StreamEx;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.Column;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table.FullSaver;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TTableUtil
{
    public static StreamEx<Object> streamFlattened(Column column)
    {
        return new StreamEx.Emitter<Object>()
        {
            int nextIndex = 0;
            @Override
            public StreamEx.Emitter<Object> next(Consumer<? super Object> consumer)
            {
                try
                {
                    if (column.indexValid(nextIndex))
                    {
                        Object collapsed = column.getType().getCollapsed(nextIndex);
                        consumer.accept(collapsed);
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);                }
            }
        }.stream();
    }

    public static String save(TableManager tableManager) throws ExecutionException, InterruptedException, InvocationTargetException
    {
        // This thread is only pretend running on FXPlatform, but sets off some
        // code which actually runs on the fx platform thread:
        CompletableFuture<String> f = new CompletableFuture<>();

        try
        {
            FullSaver saver = new FullSaver(null);
            tableManager.save(null, saver);
            f.complete(saver.getCompleteFile());
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            f.complete("");
        }
        return f.get();
    }

    @SuppressWarnings("nullness")
    public static Map<List<Object>, Long> getRowFreq(Stream<List<Object>> src)
    {
        SortedMap<List<Object>, Long> r = new TreeMap<>((Comparator<List<Object>>)(List<Object> a, List<Object> b) -> {
            if (a.size() != b.size())
                return Integer.compare(a.size(), b.size());
            for (int i = 0; i < a.size(); i++)
            {
                try
                {
                    int cmp = Utility.compareValues(a.get(i), b.get(i));
                    if (cmp != 0)
                        return cmp;
                }
                catch (InternalException | UserException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return 0;
        });
        src.forEach(new Consumer<List<Object>>()
        {
            @Override
            public void accept(List<Object> row)
            {
                r.compute(row, new BiFunction<List<Object>, Long, Long>()
                {
                    @Override
                    public Long apply(List<Object> k, Long v)
                    {
                        return v == null ? 1 : v + 1;
                    }
                });
            }
        });
        return r;
    }

    @SuppressWarnings("nullness")
    public static Map<List<Object>, Long> getRowFreq(RecordSet src)
    {
        return getRowFreq(streamFlattened(src).<List<Object>>map(p -> p.getSecond()));
    }

    public static StreamEx<Pair<Integer, List<Object>>> streamFlattened(RecordSet src)
    {
        return new StreamEx.Emitter<Pair<Integer, List<Object>>>()
        {
            int nextIndex = 0;
            @Override
            public StreamEx.Emitter<Pair<Integer, List<Object>>> next(Consumer<? super Pair<Integer, List<Object>>> consumer)
            {
                try
                {
                    if (src.indexValid(nextIndex))
                    {
                        List<Object> collapsed = src.getColumns().stream()/*.sorted(Comparator.comparing(Column::getName))*/.map(c ->
                        {
                            try
                            {
                                return c.getType().getCollapsed(nextIndex);
                            }
                            catch (UserException | InternalException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }).collect(Collectors.toList());
                        consumer.accept(new Pair<>(nextIndex, collapsed));
                        nextIndex += 1;
                        return this;
                    }
                    else
                        return null; // No more elements
                }
                catch (UserException | InternalException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }.stream();
    }

    public static String toString(Object value)
    {
        if (value instanceof Object[])
        {
            return "(" + Arrays.stream((Object[])value).map(TTableUtil::toString).collect(Collectors.joining(",")) + ")";
        }
        else if (value instanceof ListEx)
        {
            StringBuilder sb = new StringBuilder("[");
            ListEx list = (ListEx) value;
            try
            {
                for (int i = 0; i < list.size(); i++)
                {
                    if (i != 0)
                        sb.append(", ");
                    sb.append(toString(list.get(i)));
                }
            }
            catch (InternalException | UserException e)
            {
                sb.append("ERROR...");
            }
            sb.append("]");
            return sb.toString();
        }
        else if (value instanceof TaggedValue)
        {
            TaggedValue t = ((TaggedValue)value);
            return t.getTagIndex() + (t.getInner() == null ? "" : ":" + toString(t.getInner()));
        }
        else
            return value.toString();
    }

    public static String toString(Column c) throws UserException, InternalException
    {
        StringBuilder sb = new StringBuilder("[");
        DataTypeValue t = c.getType();
        for (int i = 0; i < c.getLength(); i++)
        {
            if (i != 0)
                sb.append(", ");
            sb.append(toString(t.getCollapsed(i)));
        }
        sb.append("]");
        return sb.toString();
    }
}

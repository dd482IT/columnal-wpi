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

package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.ProgressListener;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * Created by neil on 03/01/2017.
 */
public class RecordColumnStorage extends SparseErrorColumnStorage<Record> implements ColumnStorage<Record>
{
    // A record like {a: Text, b: Number} is stored in two column storages, one for all the a values, one for all the b
    private final ImmutableMap<String, ColumnStorage<?>> storage;
    private final DataTypeValue type;

    public RecordColumnStorage(ImmutableMap<String, DataType> fields, boolean isImmediateData) throws InternalException
    {
        this(fields, null, isImmediateData);
    }

    public RecordColumnStorage(ImmutableMap<String, DataType> fields, BeforeGet<?> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        ImmutableMap.Builder<String, ColumnStorage<?>> builder = ImmutableMap.builder();
        for (Entry<String, DataType> field : fields.entrySet())
        {
            builder.put(field.getKey(), ColumnUtility.makeColumnStorage(field.getValue(), beforeGet, isImmediateData));
        }
        storage = builder.build();
        type = DataTypeValue.record(Utility.<String, ColumnStorage<?>, DataType>mapValues(storage, s -> s.getType().getType()), new GetValue<Record>()
        {
            @Override
            public Record getWithProgress(int index, ProgressListener progressListener) throws UserException, InternalException
            {
                ImmutableMap.Builder<String, Object> record = ImmutableMap.builderWithExpectedSize(fields.size());
                for (Entry<String, ColumnStorage<?>> entry : storage.entrySet())
                {
                    record.put(entry.getKey(), entry.getValue().getType().getCollapsed(index));
                }
                return DataTypeUtility.value(new RecordMap(record.build()));
            }

            @Override
            public void set(int index, Either<String, Record> value) throws InternalException, UserException
            {
                value.eitherEx_(err -> {
                    setError(index, err);
                    for (ColumnStorage<?> columnStorage : storage.values())
                    {
                        columnStorage.getType().setCollapsed(index, Either.left(err));
                    }
                }, record -> {
                    unsetError(index);
                    for (Entry<String, ColumnStorage<?>> entry : storage.entrySet())
                    {
                        entry.getValue().getType().setCollapsed(index, Either.right(record.getField(entry.getKey())));
                    }
                });
            }
        });
    }

    @Override
    public int filled()
    {
        return storage.values().stream().mapToInt(s -> s.filled()).min().orElse(0);
    }

    /*
    public Object @NonNull [] get(int index) throws InternalException, UserException
    {
        Object[] r = new Object[storage.size()];
        for (int i = 0; i < r.length; i++)
        {
            r[i] = storage.get(i).get(index);
        }
        return r;
    }*/

    @SuppressWarnings({"unchecked", "all"})
    @Override
    public void addAll(Stream<Either<String, Record>> items) throws InternalException
    {
        // Each Object[] is one tuple record, add each element to each storage
        for (Either<String, Record> item : Utility.iterableStream(items))
        {
            item.eitherInt_(s -> {
                setError(filled(), s);
                for (ColumnStorage columnStorage : storage.values())
                {
                    columnStorage.addAll(Stream.<Either<String, Object>>of(Either.<String, Object>left(s)));
                }
            }, record -> {
                for (Entry<String, ColumnStorage<?>> entry : storage.entrySet())
                {
                    ((ColumnStorage)entry.getValue()).add(record.getField(entry.getKey()));
                }
            });
        }
    }

    @Override
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<Record> items) throws InternalException
    {
        List<SimulationRunnable> reverts = new ArrayList<>();
        try
        {
            for (Entry<String, ColumnStorage<?>> entry : storage.entrySet())
            {
                @SuppressWarnings("unchecked")
                ColumnStorage<Object> storage = (ColumnStorage)entry.getValue();
                
                // Note: can't use mapList here as it contains nulls
                ArrayList<Either<String, Object>> r = new ArrayList<>();
                for (Record item : items)
                {
                    if (item == null)
                        r.add(Either.<String, Object>left(""));
                    else
                        r.add(Either.<String, Object>right(item.getField(entry.getKey())));
                }
                
                reverts.add(storage.insertRows(index, r));
            }
            return () ->
            {
                for (SimulationRunnable revert : reverts)
                {
                    revert.run();
                }
            };
        }
        catch (InternalException e)
        {
            for (SimulationRunnable revert : reverts)
            {
                revert.run();
            }
            throw e;
        }
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        List<SimulationRunnable> reverts = new ArrayList<>();
        try
        {
            for (ColumnStorage<?> columnStorage : storage.values())
            {
                reverts.add(columnStorage.removeRows(index, count));
            }
            return () ->
            {
                for (SimulationRunnable revert : reverts)
                {
                    revert.run();
                }
            };
        }
        catch (InternalException e)
        {
            for (SimulationRunnable revert : reverts)
            {
                revert.run();
            }
            throw e;
        }

    }
}

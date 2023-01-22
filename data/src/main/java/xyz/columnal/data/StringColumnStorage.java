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

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.DumbObjectPool;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage extends SparseErrorColumnStorage<String> implements ColumnStorage<String>
{
    private final ArrayList<String> values;
    @SuppressWarnings("unchecked")
    private final DumbObjectPool<String> pool = new DumbObjectPool<>((Class<String>)(Class)String.class, 1000, null);
    private final BeforeGet<StringColumnStorage> beforeGet;
    
    private DataTypeValue dataType;

    public StringColumnStorage(BeforeGet<StringColumnStorage> beforeGet, boolean isImmediateData)
    {
        super(isImmediateData);
        values = new ArrayList<>();
        this.beforeGet = beforeGet;
    }

    public StringColumnStorage(boolean isImmediateData)
    {
        this(null, isImmediateData);
    }

    @Override
    public int filled()
    {
        return values.size();
    }
    
    private String get(int index, ProgressListener progressListener) throws InternalException, UserException
    {
        if (index < 0 || index >= values.size())
            throw new UserException("Attempting to access invalid element: " + index + " of " + values.size());
        return values.get(index);
    }

    @Override
    public void addAll(Stream<Either<String, String>> items) throws InternalException
    {
        for (Either<String, String> item : Utility.iterableStream(items))
        {
            String s = item.either(err -> "", v -> v);
            item.ifLeft(err -> setError(this.values.size(), err));
            this.values.add(pool.pool(DataTypeUtility.value(s)));
        }
    }

    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.text(new GetValueOrError<String>()
            {
                @Override
                protected void _beforeGet(int index, ProgressListener progressListener) throws InternalException, UserException
                {
                    if (beforeGet != null)
                        beforeGet.beforeGet(StringColumnStorage.this, index, progressListener);

                }

                @Override
                public String _getWithProgress(int i, ProgressListener prog) throws UserException, InternalException
                {
                    return StringColumnStorage.this.get(i, prog);
                }

                @Override
                public void _set(int index, String value) throws InternalException
                {
                    if (value == null)
                        value = DataTypeUtility.value("");
                    
                    setValue(index, value);
                }
            });
        }
        return dataType;
    }

    public void setValue(int index, String value) throws InternalException
    {
        if (index == values.size())
        {
            values.add(pool.pool(value));

        }
        else
        {
            values.set(index, pool.pool(value));
        }
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<String> items) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to insert rows at invalid index: " + index + " length is: " + values.size());
        values.ensureCapacity(values.size() + items.size());
        int curIndex = index;
        for (String item : items)
        {
            if (item == null)
                item = "";
            values.add(curIndex, pool.pool(DataTypeUtility.value(item)));
            curIndex += 1;
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        if (index < 0 || index + count > values.size())
            throw new InternalException("Trying to remove rows at invalid index: " + index + " + " + count + " length is: " + values.size());
        List<String> old = new ArrayList<>(values.subList(index, index + count));
        values.subList(index, index + count).clear();
        return () -> values.addAll(index, old);
    }
}

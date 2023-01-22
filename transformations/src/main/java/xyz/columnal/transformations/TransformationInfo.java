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

package xyz.columnal.transformations;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformSupplier;
import xyz.columnal.utility.function.simulation.SimulationSupplier;

import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 02/11/2016.
 */
public abstract class TransformationInfo
{
    /**
     * The name, as will be used for saving and loading.
     */
    protected final String canonicalName;

    /**
     * The name, as will be shown in the search bar and display.
     */
    protected final String displayNameKey;

    private final String imageFileName;

    /**
     * Keywords to search (e.g. alternative names for this function).
     */
    protected final List<String> keywords;

    /**
     * The key for the text explaining this transformation
     */
    private final String explanationKey;


    public TransformationInfo(String canonicalName, String displayNameKey, String imageFileName, String explanationKey, List<String> keywords)
    {
        this.canonicalName = canonicalName;
        this.displayNameKey = displayNameKey;
        this.imageFileName = imageFileName;
        this.keywords = keywords;
        this.explanationKey = explanationKey;
    }

    public final String getCanonicalName()
    {
        return canonicalName;
    }

    public final String getDisplayNameKey()
    {
        return displayNameKey;
    }

    public abstract Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException;

    public abstract SimulationSupplier<Transformation> make(TableManager mgr, CellPosition destination, FXPlatformSupplier<Optional<Table>> askForSingleSrcTable);
    
    public final String getImageFileName()
    {
        return imageFileName;
    }


    // TransformationInfo is uniquely keyed by the canonical name:
    @Override
    public final int hashCode()
    {
        return getCanonicalName().hashCode();
    }

    @Override
    public final boolean equals(Object obj)
    {
        return obj instanceof TransformationInfo && obj != null && getCanonicalName().equals(((TransformationInfo)obj).getCanonicalName());
    }

    public final String getExplanationKey()
    {
        return explanationKey;
    }
}

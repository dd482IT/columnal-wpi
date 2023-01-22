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

package xyz.columnal.log;

import javafx.application.Platform;
import org.checkerframework.checker.i18n.qual.Localized;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.Function;

public abstract class ErrorHandler
{
    private static ErrorHandler errorHandler = new ErrorHandler()
    {
        @Override
        public void showError(String title, Function<String, String> errWrap, Exception e)
        {
            // Default if new handler not set is just to log
            String localMsg = e.getLocalizedMessage();
            Log.log(title + (localMsg == null ? "<null>" : errWrap.apply(localMsg)), e);
        }
    };

    public static ErrorHandler getErrorHandler()
    {
        return errorHandler;
    }

    public static void setErrorHandler(ErrorHandler errorHandler)
    {
        ErrorHandler.errorHandler = errorHandler;
    }

    public final void alertOnError_(String title, RunOrError r)
    {
        alertOnError_(title, err -> err, r);
    }

    public final void alertOnError_(String title, Function<String, String> errWrap, RunOrError r)
    {
        try
        {
            r.run();
        }
        catch (InternalException | UserException e)
        {
            showError(title, errWrap, e);
        }
    }

    public final void showError(String title, Exception e)
    {
        showError(title, x -> x, e);
    }

    // Note -- should not block the simulation thread!
    public abstract void showError(String title, Function<String, String> errWrap, Exception e);

    public static interface RunOrError
    {
        void run() throws InternalException, UserException;
    }

}

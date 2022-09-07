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

package test.gui.trait;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.gui.dtf.DocumentTextField;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.FXPlatformRunnable;
import xyz.columnal.utility.UnitType;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Random;

import static org.junit.Assert.*;

public interface EnterStructuredValueTrait extends FxRobotInterface, FocusOwnerTrait
{
    // Returns true if the content should be unaltered after focus leaves
    @OnThread(Tag.Any)
    default public boolean enterStructuredValue(DataType dataType, @Value Object value, Random r, boolean deleteAllFirst, boolean allowFieldShuffle) throws InternalException, UserException
    {
        return enterStructuredValue_Impl(dataType, value, r, deleteAllFirst, allowFieldShuffle, true);
    }

    // Returns true if the content should be unaltered after focus leaves
    @OnThread(Tag.Any)
    default public boolean enterStructuredValue_Impl(DataType dataType, @Value Object value, Random r, boolean deleteAllFirst, boolean allowFieldShuffle, boolean topLevel) throws InternalException, UserException
    {
        final int DELAY = 1;
        
        if (deleteAllFirst)
        {
            //FlexibleTextField view = robot.getFocusOwner(FlexibleTextField.class);
            push(TestUtil.ctrlCmd(), KeyCode.A);
            push(KeyCode.DELETE);
            push(KeyCode.HOME);
        }
        return dataType.apply(new DataTypeVisitor<Boolean>()
        {
            // Can't paste as first item, in case unfocused
            boolean haveWritten = false;
            
            private void writeOrPaste(String content)
            {
                if (haveWritten && r.nextInt(3) == 1)
                {
                    TestUtil.fx_(() -> {
                        Clipboard.getSystemClipboard().setContent(ImmutableMap.of(DataFormat.PLAIN_TEXT, content));
                    });
                    push(KeyCode.SHORTCUT, KeyCode.V);
                }
                else
                {
                    write(content, DELAY);
                    haveWritten = true;
                }
            }
            
            @Override
            public Boolean number(NumberInfo numberInfo) throws InternalException, UserException
            {                
                String num = Utility.toBigDecimal(Utility.cast(value, Number.class)).toPlainString();
                writeOrPaste(num);
                return !topLevel;
            }

            @Override
            public Boolean text() throws InternalException, UserException
            {
                @Value String stringValue = Utility.cast(value, String.class);
                if (topLevel && !stringValue.isEmpty() && !stringValue.startsWith("\"") && !stringValue.endsWith("\"") && r.nextBoolean())
                {
                    writeOrPaste(stringValue);
                    return false;
                }
                else
                {
                    writeOrPaste("\"" + GrammarUtility.escapeChars(stringValue) + "\"");
                    return true;
                }
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                TemporalAccessor t = (TemporalAccessor) value;
                if (dateTimeInfo.getType().hasYearMonth())
                {
                    write(String.format("%04d", t.get(ChronoField.YEAR)) + "-", DELAY);
                    write(String.format("%02d", t.get(ChronoField.MONTH_OF_YEAR)), DELAY);
                    if (dateTimeInfo.getType().hasDay())
                    {
                        write("-");
                        write(String.format("%02d", t.get(ChronoField.DAY_OF_MONTH)));
                    }
                    if (dateTimeInfo.getType().hasTime())
                        write(" ");
                }
                if (dateTimeInfo.getType().hasTime())
                {
                    String format = r.nextBoolean() ? "%02d" : "%d";
                    String after = null;
                    if (r.nextBoolean())
                    {
                        write(String.format(format, t.get(ChronoField.HOUR_OF_DAY)) + ":", DELAY);
                    }
                    else
                    {
                        write(String.format(format, t.get(ChronoField.CLOCK_HOUR_OF_AMPM)) + ":", DELAY);
                        after = (r.nextBoolean() ? "" : "  ") + (t.get(ChronoField.AMPM_OF_DAY) == 0 ? "AM" : "PM");
                    }
                    write(String.format("%02d", t.get(ChronoField.MINUTE_OF_HOUR)), DELAY);
                    int nano = t.get(ChronoField.NANO_OF_SECOND);
                    int second = t.get(ChronoField.SECOND_OF_MINUTE);
                    if (nano != 0 || second != 0 || r.nextInt(3) == 1)
                    {
                        write(":" + String.format("%02d", second) + (nano == 0 ? "" : "."), DELAY);
                        write(String.format("%09d", nano).replaceAll("0*$", ""), DELAY);
                    }
                    if (after != null)
                        write(after, DELAY);
                }
                if (dateTimeInfo.getType().hasZoneId())
                {
                    ZoneId zone = ((ZonedDateTime) t).getZone();
                    Log.debug("Zone: {{{" + zone + "}}} is " + zone.getId());
                    write(" ");
                    write(zone.getId(), DELAY);
                }
                
                haveWritten = true;
                
                return true;
            }

            @Override
            public Boolean bool() throws InternalException, UserException
            {
                // Delete the false which is a placeholder:
                writeOrPaste(Boolean.toString(Utility.cast(value, Boolean.class)));
                return true;
            }
            
            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                writeOrPaste(DataTypeUtility.valueToString(value));
                return true;
            }

            @Override
            public Boolean record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                write("(");
                haveWritten = true;
                @Value Record record = Utility.cast(value, Record.class);
                boolean first = true;
                ArrayList<Entry<@ExpressionIdentifier String, DataType>> entries = new ArrayList<>(fields.entrySet());
                if (allowFieldShuffle)
                    Collections.shuffle(entries, r);
                else
                    Collections.sort(entries, Comparator.comparing(e -> e.getKey()));
                for (Entry<@ExpressionIdentifier String, DataType> entry : entries)
                {
                    if (!first)
                    {
                        write(",");
                        if (r.nextBoolean())
                            write(" ");
                    }
                    first = false;
                    write(entry.getKey() + ": ");
                    enterStructuredValue_Impl(entry.getValue(), record.getField(entry.getKey()), r, false, allowFieldShuffle, false);
                }

                write(")");
                return true;
            }

            @Override
            @OnThread(value = Tag.Simulation, ignoreParent = true)
            public Boolean array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner != null)
                {
                    write("[");
                    haveWritten = true;
                    ListEx listEx = Utility.cast(value, ListEx.class);
                    for (int i = 0; i < listEx.size(); i++)
                    {
                        if (i > 0)
                        {
                            write(",");
                            if (r.nextBoolean())
                                write(" ");
                        }
                        enterStructuredValue_Impl(inner, listEx.get(i), r, false, allowFieldShuffle, false);
                    }
                    write("]");
                }
                return true;
            }
        });
    }
    
    // Checks STF has same content after running defocus
    @OnThread(Tag.Any)
    default public void defocusSTFAndCheck(boolean checkContentSame, FXPlatformRunnable defocus)
    {
        Window window = TestUtil.fx(() -> getRealFocusedWindow());
        Node node = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertTrue("" + node, node instanceof DocumentTextField);
        DocumentTextField field = (DocumentTextField) node;
        String content = TestUtil.fx(() -> field._test_getGraphicalText());
        ChangeListener<String> logTextChange = (a, oldVal, newVal) -> Log.logStackTrace("Text changed on defocus from : \"" + oldVal + "\" to \"" + newVal + "\"");
        if (checkContentSame)
        {
            //TestUtil.fx_(() -> field.textProperty().addListener(logTextChange));
        }
        TestUtil.fx_(defocus);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotEquals(node, TestUtil.fx(() -> window.getScene().getFocusOwner()));
        node = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertFalse("" + node, node instanceof DocumentTextField);
        if (checkContentSame)
        {
            assertEquals(content, TestUtil.fx(() -> field._test_getGraphicalText()));
            //TestUtil.fx_(() -> field.textProperty().removeListener(logTextChange));
        }
    }
}

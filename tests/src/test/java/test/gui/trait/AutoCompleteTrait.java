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

import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.testfx.api.FxRobotInterface;
import xyz.columnal.gui.AutoComplete.Completion;
import xyz.columnal.gui.lexeditor.completion.LexAutoCompleteWindow;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public interface AutoCompleteTrait extends FxRobotInterface
{
    // Completes in LexCompletionWindow
    public default void lexComplete(String completion)
    {
        write(completion.substring(0, 1));
        scrollLexAutoCompleteToOption(completion);
        push(KeyCode.ENTER);
    }

    @OnThread(Tag.Any)
    default void scrollLexAutoCompleteToOption(String content)
    {
        List<Window> autos = listTargetWindows().stream().filter(w -> TestUtil.fx(() -> w.isShowing())).filter(w -> w instanceof LexAutoCompleteWindow).collect(Collectors.toList());
        assertEquals(autos.stream().map(Object::toString).collect(Collectors.joining(";")), 1, autos.size());

        LexAutoCompleteWindow autoComplete = ((LexAutoCompleteWindow)window(w -> w instanceof LexAutoCompleteWindow && TestUtil.fx(() -> w.isShowing())));

        // Move to top:
        String prev = "\u0000";
        while (!Objects.equals(prev, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent())))
        {
            prev = TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent());
            push(KeyCode.PAGE_UP);
        }

        List<String> all = new ArrayList<>();

        // Now scroll down until we find it, or reach end:
        prev = "\u0000";
        while (!Objects.equals(prev, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent()))
            && !Objects.equals(content, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent())))
        {
            prev = TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent());
            if (prev != null)
                all.add(prev);
            push(KeyCode.DOWN);
        }

        assertEquals("Options: " + all.stream().collect(Collectors.joining(";")), content, TestUtil.<@Nullable String>fx(() -> autoComplete._test_getSelectedContent()));
    }


    public default void autoComplete(String completion, boolean useTab)
    {
        ListView<Completion> listView = lookup(".autocomplete").query();
        int itemCount = TestUtil.fx(() -> listView.getItems().size());
        for (int i = TestUtil.fx(() -> listView.getSelectionModel().getSelectedIndex()); i < itemCount; i++)
        {
            push(KeyCode.DOWN);
            if (Objects.equals(TestUtil.<@Nullable String>fx(() -> Utility.onNullable(listView.getSelectionModel().getSelectedItem(), x -> x._test_getContent())), completion))
            {
                push(useTab ? KeyCode.TAB : KeyCode.ENTER);
                return;
            }
        }
        Assert.fail("Could not find item: " + completion);
    }
}
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

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testfx.api.FxRobotInterface;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.EditImmediateColumnDialog.ColumnDetails;
import xyz.columnal.transformations.expression.type.TypeExpression;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public interface EnterColumnDetailsTrait extends FxRobotInterface, EnterTypeTrait, EnterStructuredValueTrait
{
    // Call once the dialog is showing.  We will click Ok button before returning.
    @OnThread(Tag.Simulation)
    default public void enterColumnDetails(ColumnDetails columnDetails, Random r) throws InternalException, UserException
    {
        // We should be focused on name initially with the whole field selected, or blank:
        assertFalse("Should be no errors showing in type editor while unfocused", lookup(".type-editor .error-underline").tryQuery().isPresent());
        write(columnDetails.columnId.getRaw(), DELAY);
        Window dialog = TestUtil.fx(() -> getRealFocusedWindow());
        Log.debug("Pressing TAB on window: " + dialog);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(columnDetails.dataType), r);
        dialog = TestUtil.fx(() -> getRealFocusedWindow());
        Log.debug("Pressing ESCAPE on window: " + dialog);
        push(KeyCode.ESCAPE);
        push(KeyCode.ESCAPE);
        //assertTrue(TestUtil.fx(() -> dialog.isShowing()));
        Node defValue = lookup(".default-value").<Node>query();
        assertNotNull(defValue);
        if (defValue != null)
        {
            @NonNull Node defValueFinal = defValue;
            assertFalse(TestUtil.fx(() -> defValueFinal.isDisabled()));
            clickOn(".default-value");
            assertTrue(TestUtil.fx(() -> defValueFinal.isFocused()));
        }
        
        
        // We should already be in the default value:
        enterStructuredValue(columnDetails.dataType, columnDetails.defaultValue, r, true, true);
        clickOn(".ok-button");
        
        // If we can still see the OK button, there was a problem.  Cancel the dialog instead:
        if (lookup(".ok-button").tryQuery().isPresent())
        {
            Log.normal("Error in dialog; cancelling");
            // Let us see what the problem is:
            TestUtil.sleep(1500);
            clickOn(".cancel-button");
            fail("Could not click OK in dialog without error");
        }
    }
}

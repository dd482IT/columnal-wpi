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

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import xyz.columnal.log.Log;
import org.controlsfx.control.PopOver;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.PointQuery;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.function.Supplier;

public interface PopupTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default public void moveAndDismissPopupsAtPos(PointQuery pointQuery)
    {
        Point2D p = pointQuery.query();
        Supplier<Boolean> popupAtMousePos = () -> {
            return TestUtil.fx(() -> {
                return Utility.filterClass(listWindows().stream(), PopupWindow.class).anyMatch(w -> {
                    return new Rectangle2D(w.getX() - 1, w.getY() - 1, w.getWidth() + 2, w.getHeight() + 2).contains(p);
                });
            });
        };
        moveTo(p);
        // Popup windows don't seem to report their locations accurately (on JavaFX 8), and I can't find
        // a work-around for this, so just speculatively middle click once anyway.
        // Ideally, middle clicking does nothing on non-popups so this should always work:
        clickOn(MouseButton.MIDDLE);
        int attempts = 0;
        while (popupAtMousePos.get() && ++attempts < 10)
        {
            System.out.println("Middle clicking to dismiss popup");
            clickOn(MouseButton.MIDDLE);
            TestUtil.delay(50);
        }
    }
    
}

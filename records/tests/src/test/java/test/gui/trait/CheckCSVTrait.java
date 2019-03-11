package test.gui.trait;

import com.google.common.primitives.Ints;
import javafx.scene.input.KeyCode;
import org.apache.commons.io.FileUtils;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public interface CheckCSVTrait extends FxRobotInterface, ScrollToTrait, ClickOnTableHeaderTrait, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    default void exportToCSVAndCheck(VirtualGrid virtualGrid, TableManager tableManager, String prefix, List<Pair<String, List<String>>> expected, TableId tableId) throws IOException, UserException, InternalException
    {
        triggerTableHeaderContextMenu(virtualGrid, tableManager, tableId);
        clickOn(".id-tableDisplay-menu-exportToCSV");
        WaitForAsyncUtils.waitForFxEvents();

        File destCSV = File.createTempFile("dest", "csv");
        destCSV.deleteOnExit();

        // Enter file name into first dialog:
        correctTargetWindow().write(destCSV.getAbsolutePath());
        push(KeyCode.ENTER);

        /* TODO add and handle options dialog
        // Press ENTER on second dialog:
        TestUtil.sleep(200);
        push(KeyCode.ENTER);

        // Dialog vanishes when export complete:
        while(lookup(".export-options-window") != null)
        {
            TestUtil.sleep(500);
        }
        */
        TestUtil.sleep(2000);
        // Wait for work queue to be empty:
        TestUtil.sim_(() -> {});

        // Now load CSV and check it:
        String actualCSV = FileUtils.readFileToString(destCSV, Charset.forName("UTF-8"));
        TestUtil.assertEqualsText(prefix, toCSV(expected), actualCSV);
    }

    @OnThread(Tag.Any)
    static String toCSV(List<Pair<String, List<String>>> csvColumns)
    {
        Set<Integer> columnLengths = csvColumns.stream().map(p -> p.getSecond().size()).collect(Collectors.toSet());
        assertEquals("Column lengths differ (column lengths: " + Utility.listToString(new ArrayList<>(columnLengths)) + ")", 1, columnLengths.size());

        int length = columnLengths.iterator().next();

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < csvColumns.size(); i++)
        {
            Pair<String, List<String>> csvColumn = csvColumns.get(i);
            s.append(quoteCSV(csvColumn.getFirst()));
            if (i < csvColumns.size() - 1)
                s.append(",");
        }
        s.append("\n");

        for (int row = 0; row < length; row++)
        {
            for (int i = 0; i < csvColumns.size(); i++)
            {
                Pair<String, List<String>> csvColumn = csvColumns.get(i);
                s.append(quoteCSV(csvColumn.getSecond().get(row)));
                if (i < csvColumns.size() - 1)
                    s.append(",");
            }
            s.append("\n");
        }

        return s.toString();
    }

    @OnThread(Tag.Any)
    static String quoteCSV(String original)
    {
        return "\"" + original.replace("\"", "\"\"\"") + "\"";
    }


    @OnThread(Tag.Simulation)
    static List<String> collapse(int length, DataTypeValue type, int... excluding) throws UserException, InternalException
    {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < length; i++)
        {
            if (!Ints.contains(excluding, i))
                r.add(DataTypeUtility.valueToString(type.getType(), type.getCollapsed(i), null));
        }
        return r;
    }
}

package records.gui.grid;

import com.google.common.collect.Sets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Line;
import threadchecker.OnThread;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class VirtualGridLineSupplier extends VirtualGridSupplier<Line>
{
    private static final String LINE_STYLE_CLASS = "virt-grid-line";
    // Maps from the position (that it is to the left/top of) to the line itself:
    private final HashMap<Integer, Line> xLinesInUse = new HashMap<>();
    private final HashMap<Integer, Line> yLinesInUse = new HashMap<>();
    
    @Override
    void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds)
    {
        double lowestX = columnBounds.getItemCoord(columnBounds.firstItemIncl);
        double highestX = columnBounds.getItemCoord(columnBounds.lastItemIncl + 1);
        double lowestY = rowBounds.getItemCoord(rowBounds.firstItemIncl);
        double highestY = rowBounds.getItemCoord(rowBounds.lastItemIncl + 1);
        
        Set<Line> linesToKeep = Sets.newIdentityHashSet();
        
        // Make sure all the intended lines are there:
        for (int i = columnBounds.firstItemIncl; i <= columnBounds.lastItemIncl; i++)
        {
            Line line = xLinesInUse.get(i);
            double x = columnBounds.getItemCoord(i);
            if (line == null)
            {
                line = new Line();
                line.getStyleClass().add(LINE_STYLE_CLASS);
                containerChildren.add(line);
                xLinesInUse.put(i, line);
            }
            // +0.5 to make line appear in the middle of the pixel:
            line.setStartX(x + 0.5);
            line.setEndX(x + 0.5);
            line.setStartY(lowestY);
            line.setEndY(highestY);
            linesToKeep.add(line);
        }

        for (int i = rowBounds.firstItemIncl; i <= rowBounds.lastItemIncl; i++)
        {
            double y = rowBounds.getItemCoord(i);
            Line line = yLinesInUse.get(i);
            if (line == null)
            {
                line = new Line();
                line.getStyleClass().add(LINE_STYLE_CLASS);
                containerChildren.add(line);
                yLinesInUse.put(i, line);
            }
            // +0.5 to make line appear in the middle of the pixel:
            line.setStartY(y + 0.5);
            line.setEndY(y + 0.5);
            line.setStartX(lowestX);
            line.setEndX(highestX);
            linesToKeep.add(line);
        }

        for (Iterator<Entry<Integer, Line>> iterator = xLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<Integer, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.contains(integerLineEntry.getKey()))
                iterator.remove();
        }
        for (Iterator<Entry<Integer, Line>> iterator = yLinesInUse.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<Integer, Line> integerLineEntry = iterator.next();
            if (!linesToKeep.contains(integerLineEntry.getKey()))
                iterator.remove();
        }
    }
}

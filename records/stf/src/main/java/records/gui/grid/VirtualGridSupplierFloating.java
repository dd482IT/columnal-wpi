package records.gui.grid;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.Sets;
import javafx.beans.binding.DoubleExpression;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

/**
 * An implementation of {@link VirtualGridSupplier} that allows nodes to related to a grid area,
 * but to float depending on that grid's position on screen.  This might be a column header,
 * a message when the table is empty, or so on.
 */
@OnThread(Tag.FXPlatform)
public class VirtualGridSupplierFloating extends VirtualGridSupplier<Node>
{
    private final Map<FloatingItem, Optional<Node>> items = new IdentityHashMap<>();
    private final List<Node> toRemove = new ArrayList<>();

    // Prevent creation from outside the package:
    VirtualGridSupplierFloating()
    {
    }
    
    @Override
    void layoutItems(ContainerChildren containerChildren, VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds)
    {
        toRemove.forEach(r -> containerChildren.remove(r));
        toRemove.clear();
        
        for (Entry<@KeyFor("this.items") FloatingItem, Optional<Node>> item : items.entrySet())
        {
            Optional<BoundingBox> pos = item.getKey().calculatePosition(rowBounds, columnBounds);
            if (pos.isPresent())
            {
                // Should be visible; make sure there is a cell and put in right position:
                if (!item.getValue().isPresent())
                {
                    Pair<ViewOrder, Node> itemAndOrder = item.getKey().makeCell();
                    Pair<DoubleExpression, DoubleExpression> translateXY = containerChildren.add(itemAndOrder.getSecond(), itemAndOrder.getFirst());
                    item.getKey().adjustForContainerTranslation(itemAndOrder.getSecond(), translateXY);
                    item.setValue(Optional.of(itemAndOrder.getSecond()));
                }
                // Now that there's a cell there, locate it:
                FXUtility.resizeRelocate(item.getValue().get(), pos.get().getMinX(), pos.get().getMinY(), pos.get().getWidth(), pos.get().getHeight());
            }
            else
            {
                // Shouldn't be visible; is it?
                if (item.getValue().isPresent())
                {
                    containerChildren.remove(item.getValue().get());
                    item.setValue(Optional.empty());
                }
            }
        }
    }

    public final <T extends FloatingItem> T addItem(T item)
    {
        items.put(item, Optional.empty());
        return item;
    }
    
    public final void removeItem(FloatingItem item)
    {
        @Nullable Optional<Node> removed = items.remove(item);
        if (removed != null && removed.isPresent())
            toRemove.add(removed.get());
    }

    @Override
    protected @Nullable ItemState getItemState(CellPosition cellPosition)
    {
        return Utility.filterOutNulls(items.keySet().stream().<@Nullable ItemState>map(f -> f.getItemState(cellPosition))).findFirst().orElse(null);
    }

    @OnThread(Tag.FXPlatform)
    public static interface FloatingItem
    {
        // If empty is returned, means not visible (and cell is removed).  Otherwise, coords in parent are returned.
        // BoundingBox not Bounds: should be directly calculated, without passing through a coordinate transformation
        public Optional<BoundingBox> calculatePosition(VisibleDetails<@AbsRowIndex Integer> rowBounds, VisibleDetails<@AbsColIndex Integer> columnBounds);
        
        // Called when a cell is made.  If calculatePosition always returns Optional.of, then this is only called once:
        public Pair<ViewOrder, Node> makeCell();
        
        // Called once, after makeCell.
        public default void adjustForContainerTranslation(Node item, Pair<DoubleExpression, DoubleExpression> translateXY)
        {
        }

        // TODO remove the default implementatino once table and column headers implement this
        public default @Nullable ItemState getItemState(CellPosition cellPosition)
        {
            return null;
        }
    }
}

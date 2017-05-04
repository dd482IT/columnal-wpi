package utility.gui.stable;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Workers;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A customised equivalent of TableView
 */
@OnThread(Tag.FXPlatform)
public class StableView
{
    private final ObservableList<@Nullable Object> items;
    private final VirtualFlow<@Nullable Object, StableRow> virtualFlow;
    private final HBox header;
    private final VirtualizedScrollPane scrollPane;
    private final Label placeholder;
    private final StackPane stackPane; // has scrollPane and placeholder as its children

    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
    private final List<ValueFetcher> columns;
    private final List<DoubleProperty> columnSizes;
    private static final double EDGE_DRAG_TOLERANCE = 8;
    private static final double MIN_COLUMN_WIDTH = 30;
    private final List<HeaderItem> headerItems = new ArrayList<>();


    public StableView()
    {
        items = FXCollections.observableArrayList();
        header = new HBox();
        header.getStyleClass().add("stable-view-header");
        virtualFlow = VirtualFlow.<@Nullable Object, StableRow>createVertical(items, this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Object, StableRow>>(virtualFlow);
        placeholder = new Label("<Empty>");
        stackPane = new StackPane(placeholder, new BorderPane(scrollPane, header, null, null, null));
        // TODO make placeholder's visibility depend on table being empty
        stackPane.getStyleClass().add("stable-view");
        columns = new ArrayList<>();
        columnSizes = new ArrayList<>();

    }

    private StableRow makeCell(@UnknownInitialization(Object.class) StableView this, @Nullable Object data)
    {
        return new StableRow();
    }

    public Node getNode()
    {
        return stackPane;
    }

    public void setPlaceholderText(@Localized String text)
    {
        placeholder.setText(text);
    }

    public void clear()
    {
        // Clears rows, too:
        setColumns(Collections.emptyList());
    }

    public void setColumns(List<Pair<String, ValueFetcher>> columns)
    {
        items.clear();
        this.columns.clear();
        this.columns.addAll(Utility.mapList(columns, Pair::getSecond));
        while (columnSizes.size() > columns.size())
        {
            columnSizes.remove(columnSizes.size() - 1);
        }
        while (columnSizes.size() < columns.size())
        {
            columnSizes.add(new SimpleDoubleProperty(100.0));
        }

        header.getChildren().clear();
        for (int i = 0; i < columns.size(); i++)
        {
            Pair<String, ValueFetcher> column = columns.get(i);
            HeaderItem headerItem = new HeaderItem(column.getFirst(), i);
            header.getChildren().add(headerItem);
            headerItems.add(headerItem);
        }
    }

    public void setRows(SimulationFunction<Integer, Boolean> isRowValid)
    {
        Workers.onWorkerThread("Calculating table rows", () -> {
            int toAdd = 0;
            try
            {
                outer:
                for (int i = 0; i < 10; i++)
                {
                    toAdd = 0;
                    for (int j = 0; j < 10; j++)
                    {
                        if (isRowValid.apply(i * 10 + j))
                        {
                            toAdd++;
                        } else
                            break outer;
                    }
                    int toAddFinal = toAdd;
                    Platform.runLater(() ->
                    {
                        for (int k = 0; k < toAddFinal; k++)
                        {
                            items.add(null);
                        }
                    });
                }
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
                // TODO display somewhere?
            }
            int toAddFinal = toAdd;
            Platform.runLater(() -> {
                for (int k = 0; k < toAddFinal; k++)
                {
                    items.add(null);
                }
            });
        });

        //TODO store it for fetching more
    }

    private class HeaderItem extends Label
    {
        private final int itemIndex;
        private double offsetFromEdge;
        private boolean draggingLeftEdge;
        private boolean dragging = false;

        public HeaderItem(String name, int itemIndex)
        {
            super(name);
            this.itemIndex = itemIndex;
            this.getStyleClass().add("stable-view-header-item");
            this.setMinWidth(Region.USE_PREF_SIZE);
            this.setMaxWidth(Region.USE_PREF_SIZE);
            this.prefWidthProperty().bind(columnSizes.get(itemIndex));
            this.setOnMouseMoved(e -> {
                boolean nearEdge = e.getX() < EDGE_DRAG_TOLERANCE || e.getX() >= this.getWidth() - EDGE_DRAG_TOLERANCE;
                this.setCursor(dragging || nearEdge ? Cursor.H_RESIZE : null);
            });
            this.setOnMousePressed(e -> {
                if (this.getCursor() != null)
                {
                    // We always prefer to drag the right edge, as if
                    // you squish all the columns, it gives you a way out of dragging
                    // all right edges (whereas leftmost edge cannot be dragged):
                    if (e.getX() >= this.getWidth() - EDGE_DRAG_TOLERANCE)
                    {
                        dragging = true;
                        draggingLeftEdge = false;
                        // Positive offset:
                        offsetFromEdge = getWidth() - e.getX();
                    }
                    else if (itemIndex > 0 && e.getX() < EDGE_DRAG_TOLERANCE)
                    {
                        dragging = true;
                        draggingLeftEdge = true;
                        offsetFromEdge = e.getX();
                    }
                }
                e.consume();
            });
            this.setOnMouseDragged(e -> {
                // Should be true:
                if (dragging)
                {
                    if (draggingLeftEdge)
                    {
                        // Have to adjust size of column to our left
                        headerItems.get(itemIndex - 1).setRightEdgeToSceneX(e.getSceneX() - offsetFromEdge);
                    }
                    else
                    {
                        columnSizes.get(itemIndex).set(Math.max(MIN_COLUMN_WIDTH, e.getX() + offsetFromEdge));
                    }
                }
                e.consume();
            });
            this.setOnMouseReleased(e -> {
                dragging = false;
                e.consume();
            });
        }

        private void setRightEdgeToSceneX(double sceneX)
        {
            columnSizes.get(itemIndex).set(Math.max(MIN_COLUMN_WIDTH, sceneX - localToScene(getBoundsInLocal()).getMinX()));
        }
    }

    @OnThread(Tag.FXPlatform)
    private class StableRow implements Cell<@Nullable Object, Node>
    {
        private final Label lineLabel = new Label();
        private final HBox hBox = new HBox(lineLabel);
        private int curIndex = -1;

        public StableRow()
        {
            lineLabel.visibleProperty().bind(showingRowNumbers);
            lineLabel.managedProperty().bind(lineLabel.visibleProperty());
        }

        private void bindChildSize(int itemIndex)
        {
            Region n = (Region)hBox.getChildren().get(itemIndex);
            n.setMinWidth(Region.USE_PREF_SIZE);
            n.setMaxWidth(Region.USE_PREF_SIZE);
            n.prefWidthProperty().bind(columnSizes.get(itemIndex));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            return true;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int index)
        {
            if (index != curIndex)
            {
                curIndex = index;
                lineLabel.setText(Integer.toString(index));
                hBox.getChildren().setAll(lineLabel);
                bindChildSize(0);
                for (ValueFetcher column : columns)
                {
                    // This will be column + 1, if we are displaying line numbers:
                    int itemIndex = hBox.getChildren().size();
                    hBox.getChildren().add(new Label(""));
                    bindChildSize(itemIndex - 1);
                    column.fetchValue(index, (x, n) -> {
                        hBox.getChildren().set(itemIndex, n);
                        bindChildSize(itemIndex - 1);
                    });
                }
            }
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return hBox;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueReceiver
    {
        public void setValue(int rowIndex, Region value);
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueFetcher
    {
        // Called to fetch a value.  Once available, received should be called.
        // Until then it will be blank.  You can call receiver multiple times though,
        // so you can just call it with a placeholder before returning.
        public void fetchValue(int rowIndex, ValueReceiver receiver);
    }
}

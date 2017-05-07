package utility.gui.stable;

import javafx.application.Platform;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.reactfx.value.Val;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A customised equivalent of TableView
 *
 * The internal node architecture is a bit complicated.
 *
 * .stable-view: StackPane
 *     .stable-view-placeholder: Label
 *     *: BorderPane
 *         .stable-view-scroll-pane: VirtualizedScrollPane
 *             .: VirtualFlow [table contents]
 *         .stable-view-top: BorderPane [container to hold column headers and jump to top button]
 *             .stable-view-top-left: Region [item to take up top-left space]
 *             .stable-view-header: BorderPane [needed to clip and position the actual header items]
 *                 .: HBox [actual container of column headers]
 *                     .stable-view-header-item*: HeaderItem [each column header]
 *             .stable-button-top-wrapper: BorderPane [container to occupy spare height above the jump to top button]
 *                 .stable-button-top: Button [button to jump to top]
 *                     .stable-view-button-arrow: Region [the arrow graphic]
 *         .stable-view-left: BorderPane [container to hold row headers and jump to top button]
 *             .stable-view-side: BorderPane [needed to have clip and shadow for row numbers]
 *                 .: VirtualFlow [row numbers]
 *             .stable-button-left-wrapper: BorderPane [container to occupy spare height above the jump to left button]
 *                 .stable-button-left: Button [button to jump to left]
 *                     .stable-view-button-arrow: Region [the arrow graphic]
 *         .stable-view-row-numbers: StackPane [ ... ]
 */

@OnThread(Tag.FXPlatform)
public class StableView
{
    private final ObservableList<@Nullable Object> items;
    private final VirtualFlow<@Nullable Object, StableRow> virtualFlow;
    private final VirtualFlow<@Nullable Object, LineNumber> lineNumbers;
    private final HBox headerItemsContainer;
    private final VirtualizedScrollPane scrollPane;
    private final Label placeholder;
    private final StackPane stackPane; // has scrollPane and placeholder as its children

    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
    private final List<ValueFetcher> columns;
    private final List<DoubleProperty> columnSizes;
    private static final double EDGE_DRAG_TOLERANCE = 8;
    private static final double MIN_COLUMN_WIDTH = 30;
    private final List<HeaderItem> headerItems = new ArrayList<>();
    private final ScrollBar hbar;
    private final ScrollBar vbar;
    private final DropShadow leftDropShadow;
    private final DropShadow topDropShadow;
    private final DropShadow topLeftDropShadow;
    private boolean atTop;
    private boolean atLeft;
    private final ObjectProperty<Pair<Integer, Double>> topShowingCellProperty = new SimpleObjectProperty<>(new Pair<>(0, 0.0));

    private final ObjectProperty<@Nullable Pair<Integer, Integer>> focusedCell = new SimpleObjectProperty<>(null);


    public StableView()
    {
        items = FXCollections.observableArrayList();
        headerItemsContainer = new HBox();
        final Pane header = new Pane(headerItemsContainer);
        header.getStyleClass().add("stable-view-header");
        virtualFlow = VirtualFlow.<@Nullable Object, StableRow>createVertical(items, this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Object, StableRow>>(virtualFlow);
        scrollPane.getStyleClass().add("stable-view-scroll-pane");
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        hbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.HORIZONTAL).findFirst().get();
        vbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.VERTICAL).findFirst().get();
        lineNumbers = VirtualFlow.createVertical(items, x -> new LineNumber());
        // Need to prevent independent scrolling on the line numbers:
        lineNumbers.addEventFilter(ScrollEvent.SCROLL, e -> {
            e.consume();
        });
        final BorderPane lineNumberWrapper = new BorderPane(lineNumbers);
        lineNumberWrapper.getStyleClass().add("stable-view-side");
        placeholder = new Label("<Empty>");
        placeholder.getStyleClass().add(".stable-view-placeholder");
        
        Button topButton = new Button("", makeButtonArrow());
        topButton.getStyleClass().addAll("stable-view-button", "stable-view-button-top");
        topButton.setOnAction(e -> virtualFlow.scrollYToPixel(0));
        FXUtility.forcePrefSize(topButton);
        topButton.prefWidthProperty().bind(vbar.widthProperty());
        topButton.prefHeightProperty().bind(topButton.prefWidthProperty());
        BorderPane.setAlignment(topButton, Pos.BOTTOM_RIGHT);
        Region topLeft = new Region();
        topLeft.getStyleClass().add("stable-view-top-left");
        FXUtility.forcePrefSize(topLeft);
        topLeft.setMaxHeight(Double.MAX_VALUE);
        topLeft.prefWidthProperty().bind(lineNumberWrapper.widthProperty());
        Pane top = new BorderPane(header, null, GUI.wrap(topButton, "stable-button-top-wrapper"), null, topLeft);
        top.getStyleClass().add("stable-view-top");

        Button leftButton = new Button("", makeButtonArrow());
        leftButton.getStyleClass().addAll("stable-view-button", "stable-view-button-left");
        leftButton.setOnAction(e -> virtualFlow.scrollXToPixel(0));
        leftButton.prefHeightProperty().bind(hbar.heightProperty());
        leftButton.prefWidthProperty().bind(leftButton.prefHeightProperty());
        FXUtility.forcePrefSize(leftButton);
        BorderPane.setAlignment(leftButton, Pos.BOTTOM_RIGHT);
        Pane left = new BorderPane(lineNumberWrapper, null, null, GUI.wrap(leftButton, "stable-button-left-wrapper"), null);
        left.getStyleClass().add("stable-view-left");

        Button bottomButton = new Button("", makeButtonArrow());
        bottomButton.getStyleClass().addAll("stable-view-button", "stable-view-button-bottom");
        bottomButton.setOnAction(e -> virtualFlow.scrollYToPixel(Double.MAX_VALUE));
        FXUtility.forcePrefSize(bottomButton);
        bottomButton.prefWidthProperty().bind(vbar.widthProperty());
        bottomButton.prefHeightProperty().bind(bottomButton.prefWidthProperty());
        StackPane.setAlignment(bottomButton, Pos.BOTTOM_RIGHT);
        
        stackPane = new StackPane(placeholder, new BorderPane(scrollPane, top, null, null, left), bottomButton);
        headerItemsContainer.layoutXProperty().bind(virtualFlow.breadthOffsetProperty().map(d -> -d));
        placeholder.managedProperty().bind(placeholder.visibleProperty());
        stackPane.getStyleClass().add("stable-view");
        columns = new ArrayList<>();
        columnSizes = new ArrayList<>();

        Rectangle headerClip = new Rectangle();
        headerClip.widthProperty().bind(header.widthProperty());
        headerClip.heightProperty().bind(header.heightProperty().add(10.0));
        header.setClip(headerClip);

        Rectangle sideClip = new Rectangle();
        sideClip.widthProperty().bind(lineNumbers.widthProperty().add(10.0));
        sideClip.heightProperty().bind(lineNumbers.heightProperty());
        lineNumberWrapper.setClip(sideClip);

        // CSS doesn't let us have different width to height, which we need to prevent
        // visible curling in at the edges:
        topDropShadow = new DropShadow();
        topDropShadow.setBlurType(BlurType.GAUSSIAN);
        topDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        topDropShadow.setOffsetY(2);
        topDropShadow.setHeight(8);
        topDropShadow.setWidth(0);
        topDropShadow.setSpread(0.4);
        leftDropShadow = new DropShadow();
        leftDropShadow.setBlurType(BlurType.GAUSSIAN);
        leftDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        leftDropShadow.setHeight(0);
        leftDropShadow.setWidth(8);
        leftDropShadow.setOffsetX(2);
        leftDropShadow.setSpread(0.4);
        // Copy of topDropShadow, but with input of leftDropShadow:
        topLeftDropShadow = new DropShadow();
        topLeftDropShadow.setBlurType(BlurType.GAUSSIAN);
        topLeftDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        topLeftDropShadow.setOffsetX(2);
        topLeftDropShadow.setOffsetY(2);
        topLeftDropShadow.setHeight(8);
        topLeftDropShadow.setWidth(8);
        topLeftDropShadow.setSpread(0.4);
        
        
        FXUtility.listen(virtualFlow.visibleCells(), c -> {
            if (!c.getList().isEmpty())
            {
                StableRow firstVisible = c.getList().get(0);
                int firstVisibleRowIndex = firstVisible.getCurRowIndex();
                StableRow lastVisible = c.getList().get(c.getList().size() - 1);
                int lastVisibleRowIndex = lastVisible.getCurRowIndex();
                double topY = virtualFlow.cellToViewport(firstVisible, 0, 0).getY();
                double bottomY = virtualFlow.cellToViewport(lastVisible, 0, lastVisible.getNode().getHeight() - 4).getY();
                //FXUtility.setPseudoclass(header, "pinned", topY >= 5 || firstVisibleRowIndex > 0);
                atTop = topY < 5 && firstVisibleRowIndex == 0;
                updateShadows(header, lineNumberWrapper, topLeft);
                FXUtility.setPseudoclass(stackPane, "at-top", topY < 1 && firstVisibleRowIndex == 0);
                FXUtility.setPseudoclass(stackPane, "at-bottom", lastVisibleRowIndex == items.size() - 1 && bottomY < virtualFlow.getHeight());
                lineNumbers.showAtOffset(firstVisibleRowIndex, topY);
                topShowingCellProperty.set(new Pair<>(firstVisibleRowIndex, topY));
            }
        });

        
        FXUtility.addChangeListenerPlatformNN(virtualFlow.breadthOffsetProperty(), d -> {
            //FXUtility.setPseudoclass(lineNumbers, "pinned", d >= 5);
            atLeft = d < 5;
            updateShadows(header, lineNumberWrapper, topLeft);
            FXUtility.setPseudoclass(stackPane, "at-left", d < 1);
            FXUtility.setPseudoclass(stackPane, "at-right", d >= headerItemsContainer.getWidth() - virtualFlow.getWidth() - 3);
        });
    }

    public void scrollToTopLeft()
    {
        virtualFlow.scrollYToPixel(0);
        virtualFlow.scrollXToPixel(0);
    }

    @RequiresNonNull({"topDropShadow", "leftDropShadow", "topLeftDropShadow"})
    private void updateShadows(@UnknownInitialization(Object.class) StableView this, Node top, Node left, Node topLeft)
    {
        if (!atTop)
        {
            top.setEffect(topDropShadow);
            topLeft.setEffect(atLeft ? topDropShadow : topLeftDropShadow);
        }
        else
        {
            top.setEffect(null);
            topLeft.setEffect(atLeft ? null : leftDropShadow);
        }

        left.setEffect(atLeft ? null : leftDropShadow);
    }

    private static Node makeButtonArrow()
    {
        Region s = new Region();
        s.getStyleClass().add("stable-view-button-arrow");
        return s;
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

    /**
     * A pair with the row index and Y-offset of the top showing cell,
     * listen to this if you want to act on scroll.
     * @return
     */
    public ObjectExpression<Pair<Integer, Double>> topShowingCell()
    {
        return topShowingCellProperty;
    }

    public void clear()
    {
        // Clears rows, too:
        setColumns(Collections.emptyList());
    }

    public void setColumns(List<Pair<String, ValueFetcher>> columns)
    {
        // Important to clear the items, as we need to make new cells
        // which will have the updated number of columns
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

        headerItemsContainer.getChildren().clear();
        for (int i = 0; i < columns.size(); i++)
        {
            Pair<String, ValueFetcher> column = columns.get(i);
            HeaderItem headerItem = new HeaderItem(column.getFirst(), i);
            headerItemsContainer.getChildren().add(headerItem);
            headerItems.add(headerItem);
        }

        placeholder.setVisible(columns.isEmpty());

        scrollToTopLeft();
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

    public DoubleExpression topHeightProperty()
    {
        return headerItemsContainer.heightProperty();
    }

    // Column Index, Row Index
    public ObjectExpression<@Nullable Pair<Integer, Integer>> focusedCellProperty()
    {
        return focusedCell;
    }

    public int getColumnCount()
    {
        return columns.size();
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
    private class StableRow implements Cell<@Nullable Object, Region>
    {
        private final HBox hBox = new HBox();
        private final ArrayList<Pane> cells = new ArrayList<>();
        private int curRowIndex = -1;

        public StableRow()
        {
            hBox.getStyleClass().add("stable-view-row");
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
            {
                Pane pane = new StackPane();
                pane.getStyleClass().add("stable-view-row-cell");
                FXUtility.forcePrefSize(pane);
                pane.prefWidthProperty().bind(columnSizes.get(columnIndex));
                pane.setOnMouseClicked(e -> {
                    if (!pane.getChildren().isEmpty())
                        pane.getChildren().get(0).requestFocus();
                    e.consume();
                });
                cells.add(pane);
            }
            hBox.getChildren().setAll(cells);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            return true;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int rowIndex)
        {
            if (rowIndex != curRowIndex)
            {
                curRowIndex = rowIndex;
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
                {
                    ValueFetcher column = columns.get(columnIndex);
                    int columnIndexFinal = columnIndex;
                    column.fetchValue(rowIndex, (x, n) ->
                    {
                        Pane cell = cells.get(columnIndexFinal);
                        n.setFocusTraversable(true);
                        FXUtility.addChangeListenerPlatformNN(n.focusedProperty(), gotFocus -> {
                            FXUtility.setPseudoclass(cell, "focused-cell", gotFocus);
                            focusedCell.set(gotFocus ? new Pair<>(columnIndexFinal, rowIndex) : null);
                        });
                        cell.getChildren().setAll(n);
                    });
                }
            }
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Region getNode()
        {
            return hBox;
        }

        public int getCurRowIndex()
        {
            return curRowIndex;
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

    @OnThread(Tag.FXPlatform)
    private class LineNumber implements Cell<@Nullable Object, Node>
    {
        private final Label label = new Label();
        private final Pane labelWrapper = new StackPane(label);

        public LineNumber()
        {
            FXUtility.forcePrefSize(labelWrapper);
            labelWrapper.getStyleClass().add("stable-view-row-number");
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
            label.setText(Integer.toString(index));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return labelWrapper;
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }
    }
}

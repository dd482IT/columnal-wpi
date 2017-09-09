package records.gui;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.error.InternalException;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationSupplier;
import utility.gui.FXUtility;

import java.util.stream.Collectors;

/**
 * A control for entering the name of a table which is the source of a transformation.
 */
@OnThread(Tag.FXPlatform)
public class SingleSourceControl extends HBox implements CompletionListener
{
    private final ObjectProperty<@Nullable TableId> curSelectionId;
    private final ObjectExpression<@Nullable Table> curSelection;
    private final TableManager mgr;
    private final AutoComplete autoComplete;

    @SuppressWarnings("initialization") // Because we pass this to AutoComplete
    public SingleSourceControl(View view, TableManager mgr, @Nullable TableId srcTableId)
    {
        this.mgr = mgr;
        this.curSelectionId = new SimpleObjectProperty<>(srcTableId);
        this.curSelection = FXUtility.<@Nullable TableId, @Nullable Table>mapBindingEager(curSelectionId, id -> id == null ? null : mgr.getSingleTableOrNull(id));
        getStyleClass().add("single-source-control");
        Label label = new Label("Source:");
        TextField selected = new TextField(srcTableId == null ? "" : srcTableId.getOutput());
        autoComplete = new AutoComplete(selected, (s, q) -> mgr.getAllTables().stream().filter(t -> t.getId().getOutput().contains(s)).map(TableCompletion::new).collect(Collectors.<Completion>toList()), this, c -> false);
        Button select = new Button("Choose...");
        select.setOnAction(e -> {
            if (getScene() != null && getScene().getWindow() != null)
            {
                Window window = getScene().getWindow();
                window.hide(); // Or fold up?
                view.pickTable(picked ->
                {
                    if (picked != null)
                        selected.setText(picked.getId().getOutput());
                    ((Stage) window).show();
                });
            }
        });
        // TODO implement it

        label.setMinWidth(USE_PREF_SIZE);
        select.setMinWidth(USE_PREF_SIZE);
        HBox.setHgrow(selected, Priority.ALWAYS);
        getChildren().addAll(label, selected, select);
    }

    public @Nullable TableId getTableIdOrNull()
    {
        return curSelectionId.get();
    }

    @Pure
    public @Nullable Table getTableOrNull()
    {
        @Nullable TableId cur = curSelectionId.get();
        return cur == null ? null : mgr.getSingleTableOrNull(cur);
    }

    public SimulationSupplier<TableId> getTableIdSupplier()
    {
        TableId cur = curSelectionId.get();
        return () ->
        {
            if (cur == null)
            {
                throw new InternalException("Trying to create transformation even though source table is unspecified");
            }
            return cur;
        };
    }

    public ObservableObjectValue<@Nullable TableId> tableIdProperty()
    {
        return curSelectionId;
    }

    // CompletionListener methods:

    @Override
    public String doubleClick(String currentText, Completion selectedItem)
    {
        return ((TableCompletion)selectedItem).t.getId().getOutput();
    }

    @Override
    public String nonAlphabetCharacter(String textBefore, @Nullable Completion selectedItem, String textAfter)
    {
        return textBefore + textAfter; // Shouldn't happen as not using alphabets
    }

    @Override
    public String keyboardSelect(String currentText, Completion selectedItem)
    {
        return ((TableCompletion)selectedItem).t.getId().getOutput();
    }

    @Override
    public String exactCompletion(String currentText, Completion selectedItem)
    {
        return ((TableCompletion)selectedItem).t.getId().getOutput();
    }

    public ObjectExpression<@Nullable Table> tableProperty()
    {
        return curSelection;
    }

    private static class TableCompletion extends Completion
    {
        private final Table t;

        public TableCompletion(Table t)
        {
            this.t = t;
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(t.getId().getOutput()));
        }

        @Override
        public boolean shouldShow(String input)
        {
            return true;
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            boolean match = input.equals(t.getId().getOutput());
            if (match && onlyAvailableCompletion)
                return CompletionAction.COMPLETE_IMMEDIATELY;
            else if (match || onlyAvailableCompletion)
                return CompletionAction.SELECT;
            else
                return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            // I don't believe this will end up being called anyway as we don't use alphabets:
            return t.getId().getOutput().contains("" + character);
        }
    }
}

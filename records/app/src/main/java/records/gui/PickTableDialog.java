package records.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Table;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.LightDialog;

import java.util.Optional;
import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class PickTableDialog extends LightDialog<Table>
{
    private final AutoComplete autoComplete;

    public PickTableDialog(View view, Point2D lastScreenPos)
    {
        // We want the cancel button to appear to the right, because otherwise the auto complete hides it:
        super(view.getWindow(), new DialogPane() {
            private @MonotonicNonNull ButtonBar buttonBar;
            
            private ButtonBar getButtonBar()
            {
                // Not the worst hack; better than using CSS:
                if (buttonBar == null)
                    buttonBar = Utility.filterClass(getChildren().stream(), ButtonBar.class).findFirst().orElse(new ButtonBar());
                return buttonBar;
            }
            
            @Override
            protected void layoutChildren()
            {
                final double leftPadding = snappedLeftInset();
                final double topPadding = snappedTopInset();
                final double rightPadding = snappedRightInset();
                final double bottomPadding = snappedBottomInset();
                
                double w = getWidth() - leftPadding - rightPadding;
                double h = getHeight() - topPadding - bottomPadding;
                
                double buttonBarWidth = getButtonBar().prefWidth(h);
                double buttonBarHeight = getButtonBar().minHeight(buttonBarWidth);
                // We align button bar to the bottom, to get cancel to line up with text field
                // Bit of a hack: we adjust for content's padding
                double contentBottomPadding = getContent() instanceof Pane ? ((Pane)getContent()).snappedBottomInset() : 0;
                getButtonBar().resizeRelocate(leftPadding + w - buttonBarWidth, topPadding + h - buttonBarHeight - contentBottomPadding, buttonBarWidth, buttonBarHeight);
                Optional.ofNullable(getContent()).ifPresent(c -> c.resizeRelocate(leftPadding, topPadding, w - buttonBarWidth, h));
            }

            @Override
            protected double computeMinWidth(double height)
            {
                return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.minWidth(height)).orElse(0.0) + getButtonBar().minWidth(height);
            }

            @Override
            protected double computeMinHeight(double width)
            {
                return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.minHeight(width)).orElse(0.0), getButtonBar().minHeight(width));
            }

            @Override
            protected double computePrefWidth(double height)
            {
                return snappedLeftInset() + snappedRightInset() + Optional.ofNullable(getContent()).map(n -> n.prefWidth(height)).orElse(0.0) + getButtonBar().prefWidth(height);
            }

            @Override
            protected double computePrefHeight(double width)
            {
                return snappedTopInset() + snappedBottomInset() + Math.max(Optional.ofNullable(getContent()).map(n -> n.prefHeight(width)).orElse(0.0), getButtonBar().prefHeight(width));
            }
        });
        initModality(Modality.NONE);

        TextField selected = new TextField();
        autoComplete = new AutoComplete(selected, (s, q) -> view.getManager().getAllTables().stream().filter(t -> t.getId().getOutput().contains(s)).map(TableCompletion::new).collect(Collectors.<Completion>toList()), getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, c -> false);
        getDialogPane().setContent(new BorderPane(selected, new Label("Click on a table or type table name"), null, null, null));

        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        setResultConverter(bt -> null);
        centreDialogButtons();
        
        setOnShowing(e -> {
            view.enableTablePickingMode(lastScreenPos, t -> {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(this).setResult(t);
                close();
            });
            selected.requestFocus();
        });
        setOnHiding(e -> {
            view.disableTablePickingMode();
        });
        getDialogPane().getStyleClass().add("pick-table-dialog");
        //org.scenicview.ScenicView.show(getDialogPane().getScene());
    }

    private CompletionListener getListener(@UnknownInitialization(Dialog.class) PickTableDialog this)
    {
        return new CompletionListener()
        {
            @Override
            public String doubleClick(String currentText, Completion selectedItem)
            {
                // We shouldn't need the mouse call here, I think this is a checker framework bug:
                FXUtility.mouse(PickTableDialog.this).setResult(((TableCompletion)selectedItem).t);
                close();
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
                return doubleClick(currentText, selectedItem);
            }

            @Override
            public String exactCompletion(String currentText, Completion selectedItem)
            {
                return doubleClick(currentText, selectedItem);
            }
            
            @Override
            public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
            {
                if (selectedItem != null)
                    return ((TableCompletion)selectedItem).t.getId().getOutput();
                else
                    return currentText;
            }
        };
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

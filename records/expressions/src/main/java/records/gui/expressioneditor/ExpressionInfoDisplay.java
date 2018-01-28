package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import records.data.TableManager;
import records.gui.FixList;
import records.gui.FixList.FixInfo;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.expression.Expression;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;
import java.util.OptionalInt;

/**
 * Manages the display of errors, quick fixes and type information in the expression editor.
 * 
 * The rule for when it displays is either:
 *  - The user hovers over the label at the top of an expression item (regardless of error state), OR
 *  - (The user hovers over the main part or focuses the main part) AND there is an error.
 * Bindings to do this will get too intricate, so we just keep track of the properties involved
 *  
 * Additionally, when we should hide, but there is an error showing, we hide slowly, so the user gets
 * time to mouse-over us to access the fixes and so on.  If the user mouses-over us, we cancel any current
 * hide.
 *  
 */
public class ExpressionInfoDisplay
{
    private final SimpleStringProperty type = new SimpleStringProperty("");
    private final SimpleObjectProperty<StyledString> errorMessage = new SimpleObjectProperty<>(StyledString.s(""));
    private final SimpleObjectProperty<ImmutableList<FixInfo>> fixes = new SimpleObjectProperty<>(ImmutableList.of());
    private final VBox expressionNode;
    private @Nullable ErrorMessagePopup popup = null;
    private boolean focused = false;
    private boolean hoveringAttached = false;
    private boolean hoveringTopOfAttached = false;
    private boolean hoveringPopup = false;
    private @Nullable Animation hidingAnimation;

    @SuppressWarnings("initialization")
    public ExpressionInfoDisplay(VBox expressionNode, Label topLabel, TextField textField)
    {
        this.expressionNode = expressionNode;
        FXUtility.addChangeListenerPlatformNN(textField.focusedProperty(), this::textFieldFocusChanged);
        FXUtility.addChangeListenerPlatformNN(expressionNode.hoverProperty(), b -> {
            hoveringAttached = b;
            mouseHoverStatusChanged();
        });
        FXUtility.addChangeListenerPlatformNN(topLabel.hoverProperty(), b -> {
            hoveringTopOfAttached = b;
            mouseHoverStatusChanged();
        });
        FXUtility.addChangeListenerPlatformNN(errorMessage, s -> updateShowHide(true));
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            OptionalInt fKey = FXUtility.FKeyNumber(e.getCode());
            if (e.isShiftDown() && fKey.isPresent() && popup != null)
            {
                e.consume();
                hide(true);
                // 1 is F1, but should trigger fix zero:
                fixes.get().get(fKey.getAsInt() - 1).executeFix.run();
            }
        });
    }

    public void hideImmediately()
    {
        if (popup != null)
            hide(true);
    }

    public void clearError()
    {
        errorMessage.set(StyledString.s(""));
        fixes.set(ImmutableList.of());
    }

    private class ErrorMessagePopup extends PopOver
    {
        private final FixList fixList;

        public ErrorMessagePopup()
        {
            setDetachable(true);
            getStyleClass().add("expression-info-popup");
            setArrowLocation(ArrowLocation.BOTTOM_CENTER);
            // If we let the position vary to fit on screen, we end up with the popup bouncing in and out
            // as the mouse hovers on item then on popup then hides.  Better to let the item be off-screen
            // and let the user realise they need to move things about a bit:
            setAutoFix(false);
            // It's the skin that binds the height, so we must unbind after the skin is set:
            FXUtility.onceNotNull(skinProperty(), skin -> {
                // By default, the min width and height are the same, to allow for arrow + corners.
                // But we know arrow is on bottom, so we don't need such a large min height:
                getRoot().minHeightProperty().unbind();
                getRoot().setMinHeight(20.0);
            });
            
            
            TextFlow errorLabel = new TextFlow(errorMessage.get().toGUI().toArray(new Node[0]));
            errorLabel.getStyleClass().add("expression-info-error");
            errorLabel.setVisible(!errorMessage.get().toPlain().isEmpty());
            FXUtility.addChangeListenerPlatformNN(errorMessage, err -> {
                errorLabel.getChildren().setAll(err.toGUI());
                errorLabel.setVisible(!err.toPlain().isEmpty());
                updateShowHide(true);
            });
            errorLabel.managedProperty().bind(errorLabel.visibleProperty());

            fixList = new FixList(ImmutableList.of());
            fixList.setFixes(fixes.get());
            
            Label typeLabel = new Label();
            typeLabel.getStyleClass().add("expression-info-type");
            typeLabel.textProperty().bind(type);
            BorderPane container = new BorderPane(errorLabel, typeLabel, null, fixList, null);

            setContentNode(container);
            FXUtility.addChangeListenerPlatformNN(getRoot().hoverProperty(), b -> {
                hoveringPopup = b;
                mouseHoverStatusChanged();
            });
            FXUtility.addChangeListenerPlatformNN(detachedProperty(), b -> updateShowHide(false));
        }
    }

    private void show()
    {
        // Shouldn't be non-null already, but just in case:
        if (popup != null)
        {
            hide(true);
        }
        if (expressionNode.getScene() != null)
        {
            popup = new ErrorMessagePopup();
            //Log.debug(" # Showing: " + popup);
            popup.show(expressionNode);
        }
    }

    @RequiresNonNull("popup")
    // Can't have an ensuresnull check
    private void hide(boolean immediately)
    {
        @NonNull PopOver popupFinal = popup;
        // Whether we hide immediately or not, stop any current animation:
        cancelHideAnimation();

        if (immediately)
        {
            //Log.debug("#####\n# Hiding " + popupFinal + "\n#####");
            popupFinal.hide();
            popup = null;
        }
        else
        {
            
            hidingAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(popupFinal.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(2000), new KeyValue(popupFinal.opacityProperty(), 0.0))
            );
            hidingAnimation.setOnFinished(e -> {
                if (popup != null)
                {
                    Log.debug("#####\n# Hiding (slowly) " + popup + "\n#####");
                    popup.hide();
                    popup = null;
                }
            });
            hidingAnimation.playFromStart();
        }
    }

    private void mouseHoverStatusChanged()
    {
        updateShowHide(false);
    }

    private void textFieldFocusChanged(boolean newFocused)
    {
        this.focused = newFocused;
        updateShowHide(true);
    }


    private void updateShowHide(boolean hideImmediately)
    {
        if (hoveringPopup || hoveringTopOfAttached || ((hoveringAttached || focused) && !errorMessage.get().toPlain().isEmpty()) || (popup != null && popup.isDetached()))
        {
            if (popup == null)
            {
                show();
            }
            else
            {
                // Make sure to cancel any hide animation:
                cancelHideAnimation();
            }
        }
        else
        {
            if (popup != null)
                hide(hideImmediately);
        }
    }

    //@EnsuresNull("hidingAnimation")
    private void cancelHideAnimation()
    {        
        if (hidingAnimation != null)
        {
            hidingAnimation.stop();
            hidingAnimation = null;
        }
        if (popup != null)
        {
            popup.setOpacity(1.0);
        }
    }
    
    public <EXPRESSION extends StyledShowable> void setMessageAndFixes(@Nullable Pair<StyledString, List<QuickFix<EXPRESSION>>> newMsgAndFixes, @Nullable Window parentWindow, TableManager tableManager, FXPlatformConsumer<Pair<ReplacementTarget, EXPRESSION>> replace)
    {
        if (newMsgAndFixes == null)
        {
            errorMessage.setValue(StyledString.s(""));
            // Hide the popup:
            if (popup != null)
            {
                hide(true);
            }
        }
        else
        {
            //Log.debug("Message and fixes: " + newMsgAndFixes);
            // The listener on this property should make the popup every time:
            errorMessage.set(newMsgAndFixes.getFirst());
            fixes.set(newMsgAndFixes.getSecond().stream().map(q -> new FixInfo(q.getTitle(), q.getCssClasses(), () -> {
                //Log.debug("Clicked fix: " + q.getTitle());
                if (popup != null)
                    hide(true);
                replace.consume(q.getFixedVersion(parentWindow, tableManager));
            })).collect(ImmutableList.toImmutableList()));
            if (popup != null)
            {
                popup.fixList.setFixes(fixes.get());
            }
        }
    }
    
    public boolean isShowingError()
    {
        return !errorMessage.get().toPlain().isEmpty();
    }
    
    public void setType(String type)
    {
        this.type.set(type);
    }
}

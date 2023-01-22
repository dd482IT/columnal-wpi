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

package xyz.columnal.gui.lexeditor;

import annotation.units.CanonicalLocation;
import annotation.units.DisplayLocation;
import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Path;
import javafx.scene.text.HitInfo;
import javafx.scene.text.Text;
import xyz.columnal.log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.gui.lexeditor.EditorContent.CaretMoveReason;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.DisplaySpan;
import xyz.columnal.gui.lexeditor.EditorLocationAndErrorRecorder.ErrorDetails;
import xyz.columnal.gui.lexeditor.completion.LexAutoComplete;
import xyz.columnal.gui.lexeditor.completion.LexCompletion;
import xyz.columnal.gui.lexeditor.TopLevelEditor.Focus;
import xyz.columnal.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.gui.lexeditor.completion.LexCompletionListener;
import xyz.columnal.styled.StyledString;
import xyz.columnal.styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.fx.FXPlatformConsumer;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ResourceUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.TextEditorBase;
import xyz.columnal.utility.gui.TimedFocusable;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.net.URL;
import java.util.BitSet;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class EditorDisplay extends TextEditorBase implements TimedFocusable, LexAutoComplete.EditorDisplayInterface
{
    private boolean hasBeenFocused = false;
    private long lastFocusLeft;

    public static class TokenBackground extends Style<TokenBackground>
    {
        private final ImmutableList<String> styleClasses;

        public TokenBackground(ImmutableList<String> styleClasses)
        {
            super(TokenBackground.class);
            this.styleClasses = styleClasses;
        }


        @Override
        protected void style(Text t)
        {
            // We don't style the text directly
        }

        @Override
        protected TokenBackground combine(TokenBackground with)
        {
            return new TokenBackground(Utility.concatI(styleClasses, with.styleClasses));
        }

        @Override
        protected boolean equalsStyle(TokenBackground item)
        {
            return styleClasses.equals(item.styleClasses);
        }
    }
    
    private final EditorContent<?, ?> content;
    private final LexAutoComplete autoComplete;
    private final TopLevelEditor<?, ?, ?> editor;

    public EditorDisplay(EditorContent<?, ?> theContent, FXPlatformConsumer<Integer> triggerFix, TopLevelEditor<?, ?, ?> editor)
    {
        super(ImmutableList.of());
        this.autoComplete = Utility.later(new LexAutoComplete(this, new LexCompletionListener()
        {
            @Override
            public void insert(Integer start, String text)
            {
                theContent.insert(start, text);
            }

            @Override
            public void complete(LexCompletion c)
            {
                FXUtility.mouse(EditorDisplay.this).triggerSelection(c);
            }
        }));
        this.content = theContent;
        this.editor = Utility.later(editor);
        getStyleClass().add("editor-display");
        setFocusTraversable(true);
        
        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            FXUtility.mouse(this).requestFocus();
            if (!isMouseClickImmune() && event.getButton() == MouseButton.PRIMARY)
                positionCaret(event.getX(), event.getY(), true);
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            FXUtility.mouse(this).requestFocus();
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            FXUtility.mouse(this).requestFocus();
            if (!isMouseClickImmune() && event.getButton() == MouseButton.PRIMARY)
                positionCaret(event.getX(), event.getY(), false);
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.isStillSincePress() && event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY)
            {
                FXUtility.mouse(this).requestFocus();
                if (!isMouseClickImmune())
                    selectWordAt(event.getX(), event.getY());
            }
            else if (event.isStillSincePress() && event.getClickCount() >= 3 && event.getButton() == MouseButton.PRIMARY)
            {
                FXUtility.mouse(this).requestFocus();
                if (!isMouseClickImmune())
                    selectAll();
            }
            event.consume();
        });
        
        addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
            OptionalInt fKey = FXUtility.FKeyNumber(keyEvent.getCode());
            if (keyEvent.isShiftDown() && fKey.isPresent())
            {
                // 1 is F1, but should trigger fix zero:
                triggerFix.consume(fKey.getAsInt() - 1);
            }
            
            int[] caretPositions = content.getValidCaretPositions();
            int caretPosIndex = content.getCaretPosAsValidIndex();
            int caretPosition = content.getCaretPosition();
            switch (keyEvent.getCode())
            {
                case LEFT:
                    if (caretPosition != content.getAnchorPosition() && !keyEvent.isShiftDown())
                    {
                        @SuppressWarnings("units")
                        int start = Math.min(caretPosition, content.getAnchorPosition());
                        content.positionCaret(start, true);
                    }
                    else if (FXUtility.wordSkip(keyEvent))
                        content.positionCaret(content.prevWordPosition(false), !keyEvent.isShiftDown());
                    else if (caretPosIndex > 0)
                        content.positionCaret(caretPositions[caretPosIndex - 1], !keyEvent.isShiftDown());
                    break;
                case RIGHT:
                    if (caretPosition != content.getAnchorPosition() && !keyEvent.isShiftDown())
                    {
                        @SuppressWarnings("units")
                        int end = Math.max(caretPosition, content.getAnchorPosition());
                        content.positionCaret(end, true);
                    }
                    else if (FXUtility.wordSkip(keyEvent))
                        content.positionCaret(content.nextWordPosition(), !keyEvent.isShiftDown());
                    else if (caretPosIndex + 1 < caretPositions.length)
                        content.positionCaret(caretPositions[caretPosIndex + 1], !keyEvent.isShiftDown());
                    break;
                case HOME:
                    if (caretPositions.length > 0)
                        content.positionCaret(caretPositions[0], !keyEvent.isShiftDown());
                    break;
                case END:
                    if (caretPositions.length > 0)
                        content.positionCaret(caretPositions[caretPositions.length - 1], !keyEvent.isShiftDown());
                    break;
                case DOWN:
                    if (autoComplete.isShowing())
                        autoComplete.down();
                    else
                    {
                        Point2D caretBottomOnScreen = getCaretBottomOnScreen();
                        if (caretBottomOnScreen != null)
                        {
                            Point2D p = screenToLocal(caretBottomOnScreen.add(0, 8));
                            positionCaret(p.getX(), p.getY(), !keyEvent.isShiftDown());
                        }
                    }
                    break;
                case UP:
                    if (autoComplete.isShowing())
                        autoComplete.up();
                    else
                    {
                        Point2D caretBottomOnScreen = getCaretTopOnScreen();
                        if (caretBottomOnScreen != null)
                        {
                            Point2D p = screenToLocal(caretBottomOnScreen.subtract(0, 8));
                            positionCaret(p.getX(), p.getY(), !keyEvent.isShiftDown());
                        }
                    }
                    break;
                case PAGE_DOWN:
                    if (autoComplete.isShowing())
                        autoComplete.pageDown();
                    break;
                case PAGE_UP:
                    if (autoComplete.isShowing())
                        autoComplete.pageUp();
                    break;
                case BACK_SPACE:
                    if (caretPosition != content.getAnchorPosition())
                        content.replaceSelection("");
                    else if (caretPosition > 0)
                        content.replaceText(caretPosition - CanonicalLocation.ONE, caretPosition, "");
                    break;
                case DELETE:
                    if (caretPosition != content.getAnchorPosition())
                        content.replaceSelection("");
                    else if (caretPosition < content.getText().length())
                        content.replaceText(caretPosition, caretPosition + CanonicalLocation.ONE, "");
                    break;
                case A:
                    if (keyEvent.isShortcutDown())
                    {
                        selectAll();
                    }
                    break;
                case X:
                    if (keyEvent.isShortcutDown())
                        cut();
                    break;
                case C:
                    if (keyEvent.isShortcutDown())
                        copy();
                    break;
                case V:
                    if (keyEvent.isShortcutDown())
                        paste();
                    break;
                case Z:
                    if (keyEvent.isShortcutDown())
                    {
                        if (keyEvent.isShiftDown())
                            content.redo();
                        else
                            content.undo();
                    }
                    break;
                case Y:
                    if (keyEvent.isShortcutDown())
                        content.redo();
                    break;
                case ESCAPE:
                    showCompletions(null);
                    break;
                case ENTER:
                    if (autoComplete.isShowing())
                        autoComplete.getSelectedCompletion().ifPresent(this::triggerSelection);
                    else
                        return;
                    break;
                case TAB:
                    if (autoComplete.isShowing())
                        autoComplete.getSelectedCompletion().ifPresent(this::triggerSelection);
                    else
                        this.editor.parentFocusRightOfThis(Either.left(Focus.LEFT), true);
                    break;
            }
            keyEvent.consume();
        });
        
        addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            if (FXUtility.checkKeyTyped(keyEvent))
            {
                String character = keyEvent.getCharacter();
                if (")}]\"".contains(character) && content.getCaretPosition() < content.getText().length() && content.getText().charAt(content.getCaretPosition()) == character.charAt(0) && content.areBracketsBalanced())
                {
                    // Overtype instead
                    @SuppressWarnings("units")
                    int one = 1;
                    this.content.positionCaret(content.getCaretPosition() + one, true);
                }
                else if ("({[\"".contains(character) && !content.suppressBracketMatch(content.getCaretPosition()) && content.getCaretPosition() == content.getAnchorPosition())
                {
                    this.content.replaceSelection(character);
                    if (character.equals("("))
                        this.content.replaceSelection(")", true);
                    else if (character.equals("["))
                        this.content.replaceSelection("]", true);
                    else if (character.equals("{"))
                        this.content.replaceSelection("}", true);
                    else // Add a duplicate:
                        this.content.replaceSelection(character, true);
                }
                else
                {
                    this.content.replaceSelection(character);
                }
            }
        });
        
        content.addChangeListener(() -> render(true));
        content.addCaretPositionListener((c, r) -> render(false));
        render(true);
    }

    public boolean isMouseClickImmune()
    {
        return autoComplete.isMouseClickImmune();
    }

    public void _test_doubleClickOn(Point2D screenPoint)
    {
        Point2D local = screenToLocal(screenPoint);
        selectWordAt(local.getX(), local.getY());
    }

    private void selectWordAt(double localX, double localY)
    {
        positionCaret(localX, localY, true);
        int start = content.prevWordPosition(true);
        content.positionCaret(start, true);
        int end = content.nextWordPosition();
        if (start == end)
        {
            start = content.prevWordPosition(false);
            content.positionCaret(start, true);
        }
        content.positionCaret(end, false);
        //Log.debug("Double clicked: " + start + " to " + end);
    }

    private void positionCaret(double localX, double localY, boolean moveAnchor)
    {
        HitInfo hitInfo = hitTest(localX, localY);
        if (hitInfo != null)
        {
            @SuppressWarnings("units")
            int insertionIndex = hitInfo.getInsertionIndex();
            content.positionCaret(content.mapDisplayToContent(insertionIndex, !hitInfo.isLeading()), moveAnchor);
        }
    }

    @Override
    public long lastFocusedTime()
    {
        return isFocused() ? System.currentTimeMillis() : lastFocusLeft;
    }

    public void markAsPreviouslyFocused()
    {
        hasBeenFocused = true;
        render(false);
    }

    @Override
    public void focusChanged(boolean focused)
    {
        if (!focused)
        {
            showCompletions(null);
            lastFocusLeft = System.currentTimeMillis();
        }
        super.focusChanged(focused);
        if (focused)
        {
            hasBeenFocused = true;
            content.notifyCaretPositionListeners(CaretMoveReason.FOCUSED);
        }
        else
            showAllErrors();
    }

    @SuppressWarnings("units")
    void selectAll()
    {
        content.positionCaret(0, true);
        content.positionCaret(content.getText().length(), false);
    }

    @SuppressWarnings("units")
    private void triggerSelection(LexCompletion p)
    {
        if (p.content != null)
        {
            content.replaceText(p.startPos, content.getCaretPosition(), p.content, p.startPos + p.relativeCaretPos);
        }
        else if (p.furtherDetails != null)
        {
            p.furtherDetails.ifRight(new Consumer<Pair<String, String>>()
            {
                @Override
                public void accept(Pair<String, String> furtherDetailsURL)
                {
                    SwingUtilities.invokeLater(() -> {
                        try
                        {
                            URL resource = ResourceUtility.getResource(furtherDetailsURL.getFirst());
                            if (resource != null)
                                Desktop.getDesktop().browse(resource.toURI());
                            else
                                Log.error("Did not find resource: \"" + furtherDetailsURL.getFirst() + "\"");
                        }
                        catch (Exception e)
                        {
                            Log.log(e);
                        }
                    });
                }
            });
        }
        // Hide for now, will redisplay if user moves caret or types:
        autoComplete.hide(true);
    }

    private void render(boolean contentChanged)
    {
        if (contentChanged)
            textFlow.getChildren().setAll(content.getDisplayText());
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape(contentChanged);
    }
    
    void showCompletions(ImmutableList<LexCompletionGroup> completions)
    {
        if (completions != null && isFocused())
            autoComplete.show(completions);
        else
            autoComplete.hide(false);
    }

    // How many right presses (positive) or left (negative) to
    // reach nearest end of given content?
    public int _test_getCaretMoveDistance(String targetContent)
    {
        return content._test_getCaretMoveDistance(targetContent);
    }
    
    public void _test_queueUpdateCaret()
    {
        if (caretAndSelectionNodes != null)
            caretAndSelectionNodes.queueUpdateCaretShape(true);
    }

    @Override
    public Node asNode()
    {
        return this;
    }

    public Point2D getCaretBottomOnScreen()
    {
        return getCaretBottomOnScreen(content.getCaretPosition());
    }

    @Override
    public Point2D getCaretBottomOnScreen(int caretPos)
    {
        // localToScreen can return null if not in window, hence the @Nullable return
        return localToScreen(textFlow.getClickPosFor(content.mapContentToDisplay(caretPos), VPos.BOTTOM, new Dimension2D(0, 0)).getFirst());
    }

    public Point2D getCaretTopOnScreen()
    {
        return localToScreen(textFlow.getClickPosFor(content.mapContentToDisplay(content.getCaretPosition()), VPos.TOP, new Dimension2D(0, 0)).getFirst());
    }
    
    @Override
    public int getCaretPosition()
    {
        return content.getCaretPosition();
    }
    
    public int getAnchorPosition()
    {
        return content.getAnchorPosition();
    }

    @Override
    public int getDisplayCaretPosition()
    {
        return content.getDisplayCaretPosition();
    }

    @Override
    public int getDisplayAnchorPosition()
    {
        return content.getDisplayAnchorPosition();
    }

    @Override
    @SuppressWarnings("units")
    public BitSet getErrorCharacters()
    {
        BitSet errorChars = new BitSet();
        for (ErrorDetails error : content.getErrors())
        {
            if (!hasBeenFocused)
            {
                error.caretHasLeftSinceEdit = false;
            }
            else if (error.caretHasLeftSinceEdit || !isFocused() || !error.location.touches(getCaretPosition()))
            {
                error.caretHasLeftSinceEdit = true;
                if (error.displayLocation == null)
                {
                    error.displayLocation = new DisplaySpan(content.mapContentToDisplay(error.location.start), content.mapContentToDisplay(error.location.end));
                }
                try
                {
                    if (error.displayLocation.start > error.displayLocation.end)
                        throw new InternalException("Invalid display location: " + error.displayLocation + " with error: " + error.error + " and content: " + content.getText());
                    errorChars.set(error.displayLocation.start, error.displayLocation.end);
                }
                catch (InternalException e)
                {
                    Log.log(e);
                }
            }
        }
        return errorChars;
    }

    @SuppressWarnings("units")
    public void _test_positionCaret(int caretPos)
    {
        content.positionCaret(caretPos, true);
    }
    
    public TopLevelEditor<?, ?, ?> _test_getEditor()
    {
        return editor;
    }

    public Bounds _test_getCaretBounds(int pos)
    {
        try
        {
            return textFlow.localToScreen(new Path(textFlow.caretShape(content.mapContentToDisplay(pos), true)).getBoundsInParent());
        }
        catch (Exception e)
        {
            return new BoundingBox(0, 0, 0, 0);
        }
    }
    
    public boolean hasErrors()
    {
        return !content.getErrors().isEmpty();
    }
    
    public void showAllErrors()
    {
        for (ErrorDetails error : content.getErrors())
        {
            error.caretHasLeftSinceEdit = true;
        }
        content.notifyCaretPositionListeners(CaretMoveReason.FORCED_SAVE);
        render(false);
    }

    @Override
    public ImmutableList<BackgroundInfo> getBackgrounds()
    {
        ImmutableList.Builder<BackgroundInfo> r = ImmutableList.builder();
        content.getDisplay().forEach(new BiConsumer<ImmutableList<StyledString.Style<?>>, String>()
        {
            int curPos = 0;
            @Override
            public void accept(ImmutableList<StyledString.Style<?>> styles, String text)
            {
                for (StyledString.Style<?> style : styles)
                {
                    if (style instanceof TokenBackground)
                    {
                        r.add(new BackgroundInfo(curPos, curPos + text.length(), ((TokenBackground)style).styleClasses));
                    }
                }

                curPos += text.length();
            }
        });
        return r.build();
    }

    public ImmutableList<ErrorDetails> _test_getErrors()
    {
        return content.getErrors();
    }

    @Override
    protected Point2D translateHit(double x, double y)
    {
        return new Point2D(x, y);
    }

    @Override
    protected void layoutChildren()
    {
        double wholeTextHeight = textFlow.prefHeight(getWidth());

        CaretAndSelectionNodes cs = this.caretAndSelectionNodes;

        textFlow.resizeRelocate(0, 0, getWidth(), wholeTextHeight);
   
        if (cs != null)
        {
            cs.fadeOverlay.resize(getWidth(), getHeight());
            cs.caretShape.setLayoutX(0);
            cs.caretShape.setLayoutY(0);
            cs.selectionShape.setLayoutX(0);
            cs.selectionShape.setLayoutY(0);
            cs.inverter.setLayoutX(0);
            cs.inverter.setLayoutY(0);
            cs.inverterPane.resizeRelocate(0, 0, getWidth(), getHeight());
            cs.selectionPane.resizeRelocate(0, 0, getWidth(), getHeight());
        }
    }

    @Override
    protected double computeMinHeight(double width)
    {
        return textFlow.prefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width)
    {
        return textFlow.prefHeight(width);
    }

    @Override
    protected void replaceSelection(String replacement)
    {
        content.replaceSelection(replacement);
    }

    @Override
    protected String getSelectedText()
    {
        return content.getText().substring(Math.min(content.getCaretPosition(), content.getAnchorPosition()), Math.max(content.getCaretPosition(), content.getAnchorPosition()));
    }

    @Override
    protected ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused)
    {
        return ImmutableList.of();
    }

    public void cleanup()
    {
        autoComplete.cleanup();
    }
}

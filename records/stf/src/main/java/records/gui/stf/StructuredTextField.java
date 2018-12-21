package records.gui.stf;

import com.google.common.collect.ImmutableList;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.*;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.gui.stf.Component.InsertState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A StructuredTextField, for the entry of (known a priori) statically-typed data values.  The field
 * provides any structural dividers (e.g. for a time value, the colons between the numbers, for a tuple
 * it's the brackets and commas between each item) which cannot be removed or added, and prevents
 * certain invalid inputs (and/or corrects common mistakes), as well as giving contextual content assist.
 *
 * The arrangement is as follows.  A structed text field has a single top-level "component".  A component
 * may have many sub-components, in a finite-depth nested tree.  (Actually, it's only finite-depth if the types
 * are finite-depth; may revisit this if we allow recursive types.)  In general, a component corresponds to
 * a type, although it's not always 1:1 (e.g. some of the date types are composites of a couple of helper
 * components).
 *
 * Each component has 1 or more items.  An item is either an editable portion of the field (e.g. the hours
 * in a time field), or a divider (e.g. the colon between the hours and the minutes).  Each non-divider item
 * has a separate type, to enable validation and content assist.
 *
 * Fields are altered by removal (of 1 or more characters) or insertion (which is broken down into single
 * sequential character insertions).  A removal only changes the structure (i.e. list of items and components)
 * in a list component, otherwise content is removed from each editable item and dividers are left standing.
 * Similarly, insertion only alters the structure in a list, otherwise it only alters content.
 */
@OnThread(Tag.FXPlatform)
public class StructuredTextField extends StyleClassedTextArea
{
    private final List<Item> curValue = new ArrayList<>();
    private @MonotonicNonNull EditorKit<?> editorKit;
    // Once created, list doesn't change.  But overall list
    // may change when resetContent is called:
    private ImmutableList<Suggestion> suggestions = ImmutableList.of();
    // All positions in the text area which are valid:
    private final BitSet possibleCaretPositions = new BitSet();
    // All positions in the text area which are valid and are beginning or end of words:
    private final BitSet possibleCaretWordPositions = new BitSet();
    private @Nullable PopOver fixPopup;
    private @Nullable STFAutoComplete autoComplete;
    private int completingForItem = -1;
    private boolean inSuperReplace;

    // store action takes the string value of the field, and the parsed value of the field, when it is valid.
    public StructuredTextField()
    {
        super(false);
        getStyleClass().add("structured-text-field");

        setPrefHeight(FXUtility.measureNotoSansHeight());

        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(textProperty());
        Tooltip.install(this, tooltip);
        
        // TEMP:
        /*
        FXUtility.addChangeListenerPlatformNN(textProperty(), t -> {
            Log.normalStackTrace("Edited text to " + t, 50);
        });
        FXUtility.addChangeListenerPlatformNN(caretPositionProperty(), n -> {
            Log.normalStackTrace("Caret moved to " + n, 50);
        });
        */

        FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
            if (editorKit == null)
                return;

            StructuredTextField usFocused = FXUtility.focused(this);
            usFocused.updateAutoComplete(getSelection());
            if (focused)
            {
                @Nullable CaretPositionMapper mapper = usFocused.focusGained(editorKit);
                int oldPos = getCaretPosition();
                updateDocument(editorKit);
                if (mapper != null)
                    moveTo(mapper.mapCaretPosition(oldPos));
            }
            else
            {
                usFocused.focusLost(editorKit);
                updateDocument(editorKit);
            }
        });

        FXUtility.addChangeListenerPlatformNN(selectionProperty(), sel -> {
            //Log.logStackTrace("Selection now: " + sel);
            StructuredTextField us = FXUtility.mouse(this);
            if (!inSuperReplace && editorKit != null)
            {
                if (editorKit.contentComponent.selectionChanged(sel.getStart(), sel.getEnd()))
                {
                    inSuperReplace = true;
                    superReplace(0, getLength(), makeDoc(editorKit.contentComponent.getItems()));
                    selectRange(sel.getStart(), sel.getEnd());
                    inSuperReplace = false;
                }
                us.updateAutoComplete(sel);
            }
        });

        Nodes.addInputMap(FXUtility.keyboard(this), InputMap.sequence(
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.TAB), (KeyEvent e) -> {
                FXUtility.keyboard(this).tabPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), (KeyEvent e) -> {
                FXUtility.keyboard(this).enterPressed();
                e.consume();
            }),
            InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ESCAPE), (KeyEvent e) -> {
                FXUtility.keyboard(this).escapePressed();
                e.consume();
            })
        ));
        
        Nodes.addFallbackInputMap(FXUtility.mouse(this), InputMap.consume(MouseEvent.ANY));
    }

    public void tabPressed()
    {
        if (autoComplete != null && autoComplete.isShowing())
        {
            autoComplete.fireSelected();
        }
        else if (editorKit != null)
        {
            editorKit.relinquishFocus();
        }
    }

    public void escapePressed()
    {
        if (autoComplete != null)
        {
            autoComplete.hide();
            autoComplete = null;
        }
        else if (editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    public void enterPressed()
    {
        if (autoComplete != null)
        {
            autoComplete.fireSelected();
        }
        boolean atEnd = getCaretPosition() == getLength();
        if (atEnd && editorKit != null)
            editorKit.relinquishFocus(); // Should move focus away from us
    }

    private <T> @Nullable CaretPositionMapper focusGained(EditorKit<T> editorKit)
    {
        return editorKit.focusChanged(true);
    }
    
    private <T> void focusLost(EditorKit<T> editorKit)
    {
        // Deselect when focus is lost:
        deselect();
        endEdit(editorKit).either_(x -> showFixPopup(x), v -> editorKit.storeValue(v, this));
        editorKit.focusChanged(false);
    }

    public StructuredTextField(EditorKit<?> editorKit)
    {
        this();
        // It clearly must be initialised because we called inner constructor!
        Utility.later(this).resetContent(editorKit);
    }

    public <T> void resetContent(EditorKit<T> editorKit)
    {
        if (this.editorKit != null)
        {
            getStyleClass().removeAll(this.editorKit.stfStyles);
            this.editorKit.setField(null);
        }
        
        this.editorKit = editorKit;
        this.editorKit.setField(this);
        getStyleClass().addAll(editorKit.stfStyles);
        List<Item> initialItems = editorKit.contentComponent.getItems();
        curValue.clear();
        curValue.addAll(initialItems);
        
        @SuppressWarnings("initialization")
        Either<List<ErrorFix>, T> endInitial = endEdit(editorKit);
        @Nullable T val = endInitial.<@Nullable T>either(err -> null, v -> v);
        editorKit.completedValue = val;
        editorKit.lastValidValue = editorKit.captureState(curValue, getDocument());
        suggestions = editorKit.contentComponent.getSuggestions();
        
        setEditable(this.editorKit.isEditable());
    }

    private void updateAutoComplete(javafx.scene.control.IndexRange selection)
    {
        Pair<Integer, Integer> selStr = plainToStructured(selection.getStart());
        int selLength = selection.getLength();
        if (autoComplete != null && (!isFocused() || selLength != 0 || selStr.getFirst() != completingForItem))
        {
            // Current one no longer applicable; hide
            autoComplete.hide();
            autoComplete = null;
            completingForItem = -1;
        }

        if (autoComplete == null && selLength == 0 && isFocused())
        {
            // See if there are any relevant suggestions:
            Comparator<Pair<Integer, Integer>> cmp = Pair.comparator();
            @Nullable Entry<Integer, List<Suggestion>> possSugg = new TreeMap<>(suggestions.stream().filter(s -> cmp.compare(s.startIndexIncl, selStr) <= 0 && cmp.compare(s.endIndexIncl, selStr) >= 0)
                .collect(Collectors.<Suggestion, Integer>groupingBy(s -> s.getItemsLength()))).firstEntry();
            if (possSugg != null && possSugg.getValue().size() > 0) // Size should always be > 0, but just in case
            {
                int startCharIndex = structuredToPlain(possSugg.getValue().get(0).startIndexIncl);
                @Nullable Bounds charBounds = startCharIndex + 1 <= getLength() ? getCharacterBoundsOnScreen(startCharIndex, startCharIndex + 1).orElse(null) : null;
                if (charBounds == null)
                    charBounds = localToScreen(getBoundsInLocal());
                autoComplete = new STFAutoComplete(this, possSugg.getValue());
                autoComplete.show(this, charBounds.getMinX(), charBounds.getMaxY());
                completingForItem = selStr.getFirst();
            }
        }

        if (autoComplete != null)
            autoComplete.update();
    }


    private void showFixPopup(List<ErrorFix> errorFixes)
    {
        hidePopup();
        if (getScene() == null || getScene().getWindow() == null)
            return; // Can happen during testing
        
        PopOver popup = new PopOver();
        popup.getStyleClass().add("invalid-data-input-popup");
        List<Node> fixNodes = new ArrayList<>();
        fixNodes.add(GUI.label("entry.error"));
        for (int i = 0; i < errorFixes.size(); i++)
        {
            ErrorFix errorFix = errorFixes.get(i);
            Label label = new Label(errorFix.label);
            label.getStyleClass().add("invalid-data-fix");
            label.getStyleClass().addAll(errorFix.styleClasses);
            label.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    errorFix.performFix();
                    hidePopup();
                    e.consume();
                }
            });
            fixNodes.add(label);
        }

        popup.setContentNode(new VBox(fixNodes.toArray(new Node[0])));
        popup.setDetachable(false);
        popup.setAnimated(false);
        popup.setArrowLocation(ArrowLocation.BOTTOM_CENTER);
        fixPopup = popup;
        popup.show(FXUtility.mouse(this));
    }

    private void hidePopup(@UnknownInitialization(Object.class) StructuredTextField this)
    {
        if (fixPopup != null)
        {
            fixPopup.hide(Duration.ZERO);
            fixPopup = null;
        }
    }

    private static ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> makeDoc(List<Item> subItems)
    {
        @MonotonicNonNull ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = null;
        for (Item subItem : subItems)
        {
            ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> nextSeg = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(subItem.toStyledText(), Collections.emptyList(), subItem.toStyles(), StyledText.<Collection<String>>textOps());
            doc = doc == null ? nextSeg : doc.concat(nextSeg);
        }
        if (doc != null)
        {
            return doc;
        }
        else
        {
            return ReadOnlyStyledDocument.fromString("", Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        }
    }
    
    // Checker-friendly way to call super.replace
    @SuppressWarnings("initialization")
    private void superReplace(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField this, int start, int end, StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> replacement)
    {
        super.replace(start, end, replacement);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void replace(final int start, final int end, StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> replacement)
    {
        if (editorKit == null)
            return;

        //System.err.println("Replacing: \"" + getText(start, end) + "\" with \"" + replacement.getText() + "\"");

        hidePopup();
        int insertPos = start;
        if (end > start)
        {
            insertPos += editorKit.contentComponent.delete(start, end).startDelta;
        }
        ImmutableList<Integer> replacementCodepoints = replacement.getText().codePoints().boxed().collect(ImmutableList.toImmutableList());
        if (!replacementCodepoints.isEmpty())
        {
            InsertState insertState = editorKit.contentComponent.insert(new InsertState(0, insertPos, replacementCodepoints));
            insertPos = insertState.cursorPos;
        }
/*
        List<Item> existing = new ArrayList<>(curValue);

        List<Item> newContent = new ArrayList<>();
        int[] next = replacement.getText().codePoints().toArray();
        // Need to zip together the spans, in effect.  But should never delete the boilerplate parts
        int curExisting = 0;
        int curStart = 0;
        String cur = "";
        // We want to copy across spans that end before us, and then for the one we are in, store the content
        // before us into the cur String.
        // e.g. given "17//" and start == 3, we want to copy day and divider, and set cur to ""
        //      given "17//" and start == 2, we want to copy day and set cur to "/"
        //      given "17//" and start == 1, we want to just set cur to "1"
        while (curStart <= start)
        {
            int spanEnd = curStart + existing.get(curExisting).getLength();
            // If we're just before divider, count as being at the beginning of it, not the end:
            if (start == spanEnd && (curExisting + 1 >= existing.size() || existing.get(curExisting + 1).itemVariant == ItemVariant.DIVIDER || existing.get(curExisting + 1).itemVariant == ItemVariant.TIMEZONE_PLUS_MINUS))
                break;

            if (spanEnd <= start)
            {
                Item existingItem = existing.get(curExisting);
                newContent.add(new Item(existingItem.parent, existingItem.content, existingItem.itemVariant, existingItem.prompt));
                curExisting += 1;
                curStart = spanEnd;
            } else
            {
                break;
            }
        }
        cur = getText(curStart, start);
        int replacementEnd = start;
        while (curExisting < existing.size())
        {
            // Find the next chunk of new text which could go here:
            int nextChar = 0;
            Item existingItem = existing.get(curExisting);
            ItemVariant curStyle = existingItem.itemVariant;
            String after = existingItem.content.substring(cur.length());
            if (nextChar >= next.length)
            {
                cur = after;
            }
            else
            {
                while (nextChar < next.length)
                {
                    CharEntryResult result = enterChar(existingItem, cur, after, nextChar < next.length ? OptionalInt.of(next[nextChar]) : OptionalInt.empty());
                    cur = result.result;
                    if (nextChar < next.length && result.charConsumed)
                    {
                        nextChar += 1;
                        replacementEnd = curStart + cur.length();
                    }
                    if (result.moveToNext)
                        break;
                }
            }
            newContent.add(new Item(existingItem.parent, cur, curStyle, existingItem.prompt));
            next = Arrays.copyOfRange(next, nextChar, next.length);
            curStart += existingItem.getLength();
            curExisting += 1;
            cur = "";
        }
*/
        updateDocument(editorKit);
        selectRange(insertPos, insertPos);
        Log.debug("Inserted at " + start + "," + end + " text \"" + replacement.getText() + "\" into \"" + getText() + "\" and got to " + insertPos);
        if (!possibleCaretPositions.get(getCaretPosition()))
        {
            Log.logStackTrace("Caret position invalid");
        }
        updateAutoComplete(getSelection());
    }

    private <T> void updateDocument(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField this, EditorKit<T> editorKit)
    {
        curValue.clear();
        curValue.addAll(editorKit.contentComponent.getItems());
        StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = makeDoc(curValue);
        inSuperReplace = true;
        superReplace(0, getLength(), doc);
        inSuperReplace = false;
        updatePossibleCaretPositions();
    }

    private void updatePossibleCaretPositions(@UnknownInitialization(Object.class) StructuredTextField this)
    {
        possibleCaretPositions.clear();
        possibleCaretWordPositions.clear();
        int curPos = 0;
        boolean endLastWasValid = true;
        for (int itemIndex = 0; itemIndex < curValue.size(); itemIndex++)
        {
            if (endLastWasValid)
                possibleCaretWordPositions.set(curPos);

            Item curItem = curValue.get(itemIndex);
            for (int itemSubIndex = endLastWasValid ? 0 : 1; itemSubIndex <= curItem.getLength(); itemSubIndex++)
            {
                possibleCaretPositions.set(curPos + itemSubIndex);
            }
            curPos += curItem.getScreenLength();
            endLastWasValid = curItem.getLength() == curItem.getScreenLength();
        }
        if (endLastWasValid)
            possibleCaretWordPositions.set(curPos);
    }

    protected Optional<ErrorFix> revertEditFix()
    {
        if (editorKit != null)
            return revertEditFix(editorKit);
        else
            return Optional.empty();
    }

    private <T> Optional<ErrorFix> revertEditFix(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField this, EditorKit<T> editorKit)
    {
        if (editorKit.lastValidValue == null)
            return Optional.empty();
        // Only needed out here for null checker:
        @NonNull State<T> prev = editorKit.lastValidValue;
        return Optional.of(new ErrorFix(TranslationUtility.getString("entry.fix.revert", prev.doc.getText()), "invalid-data-revert")
        {
            @Override
            @OnThread(Tag.FXPlatform)
            @RequiresNonNull("curValue")
            public void performFix()
            {
                if (prev != null)
                {
                    setState(prev, editorKit);
                }
            }
        });
    }

    public void fireSuggestion(Suggestion item)
    {
        replaceText(structuredToPlain(item.startIndexIncl), structuredToPlain(item.endIndexIncl), item.suggestion);
        // Go to next field if there is one:
        nextChar(SelectionPolicy.CLEAR);
    }

    public String getTextForItems(int startIndexIncl, int endIndexIncl)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndexIncl; i <= endIndexIncl; i++)
        {
            sb.append(curValue.get(i).content);
        }
        return sb.toString();
    }

    public void updateFromEditorKit()
    {
        if (this.editorKit != null)
            updateDocument(this.editorKit);
    }

    private static class State<T>
    {
        public final List<Item> items;
        public final ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc;
        public final T value;

        public State(List<Item> items, ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc, T value)
        {
            this.items = new ArrayList<>(items);
            this.doc = doc;
            this.value = value;
        }
    }

    private <T> void setState(@UnknownInitialization(StyleClassedTextArea.class) StructuredTextField this, State<T> valueBeforeFocus, EditorKit<T> editorKit)
    {
        curValue.clear();
        curValue.addAll(valueBeforeFocus.items);
        // Call super to avoid our own validation:
        superReplace(0, getLength(), valueBeforeFocus.doc);
        editorKit.completedValue = valueBeforeFocus.value;
    }

    // Intended for use by subclasses during fixes.  Applies content and ends edit
    protected void setValue(String content)
    {
        if (editorKit != null)
            setValue(editorKit, content);
    }

    private <T> void setValue(EditorKit<T> editorKit, String content)
    {
        replaceText(content);
        endEdit(editorKit).either_(err -> {}, v -> {
            editorKit.completedValue = v;
            editorKit.lastValidValue = editorKit.captureState(curValue, getDocument());
        });
    }

    //package-visible
    static abstract class ErrorFix
    {
        public final @Localized String label;
        public final List<String> styleClasses;

        protected ErrorFix(@Localized String label, String... styleClasses)
        {
            this.label = label;
            this.styleClasses = Arrays.asList(styleClasses);
        }

        @RequiresNonNull("curValue")
        public abstract void performFix();
    }

    /**
     * If return is null, successful.  Otherwise list is of alternatives for fixing the error (may be empty)
     *
     * We pass editorKit as a parameter rather than using the
     * member so that we can properly parameterise the type
     * of this method.
     */
    private <T> Either<List<ErrorFix>, T> endEdit(EditorKit<T> editorKit)
    {
        Either<List<ErrorFix>, T> errorOrValue = editorKit.contentComponent.endEdit(this);
        // May have been changed by errors or fixes:
        updateDocument(editorKit);
        lineEnd(SelectionPolicy.CLEAR);
        return errorOrValue;
    }

    public @Nullable EditorKit<?> getEditorKit()
    {
        return editorKit;
    }

    public static class CharEntryResult
    {
        public final String result;
        public final boolean charConsumed;
        public final boolean moveToNext;

        public CharEntryResult(String result, boolean charConsumed, boolean moveToNext)
        {
            this.result = result;
            this.charConsumed = charConsumed;
            this.moveToNext = moveToNext;
        }
    }
/*
    private CharEntryResult enterChar(Item item, String before, String after, OptionalInt c)
    {
        ItemVariant style = item.itemVariant;
        String cStr = c.isPresent() ? new String(new int[] {c.getAsInt()}, 0, 1) : "";

        if (style == ItemVariant.DIVIDER_SPECIAL && c.isPresent())
        {
            ImmutableList<Component<?>> parents = item.getParents();
            CharEntryResult charEntryResult = parents.get(parents.size() - 1).specialEntered(c.getAsInt());
            if (charEntryResult != null)
                return charEntryResult;
        }

        if (style == ItemVariant.DIVIDER || style == ItemVariant.DIVIDER_SPECIAL)
            return new CharEntryResult(before + after, !after.isEmpty(), after.isEmpty() || cStr.equals(after));

        if (c.isPresent())
        {
            if (style.validCharacterForItem(before, c.getAsInt()))
            {
                if (before.length() < maxLength(style))
                    return new CharEntryResult(before + cStr, true, false);
                else
                    return new CharEntryResult(before, true, !before.isEmpty() && after.isEmpty());
            }
            else
            {
                if (!before.isEmpty() && after.isEmpty())
                    return new CharEntryResult(before, false, true);
                else
                    return new CharEntryResult(before, true, false);
            }
        }
        else
            return new CharEntryResult(before + after.trim(), true, true);

    }
*/
    public void edit(@Nullable Point2D scenePoint)
    {
        if (!isEditable())
            return;

        if (scenePoint != null)
        {
            Point2D local = sceneToLocal(scenePoint);
            CharacterHit hit = hit(local.getX(), local.getY());
            int characterPosition = hit.getInsertionIndex();

            moveTo(characterPosition, SelectionPolicy.CLEAR);
            requestFocus();
        }
        else
        {
            requestFocus();
            selectAll();
        }
    }

    // Each item can either be editable (with a possibly-empty starting value, and optional prompt text)
    // or boilerplate (with a fixed value).  Fields can be optional
    // E.g. time might be Hour, fixed-:, minute, optional fixed-:, optional second (we don't bother having the dot as fixed boilerplate)
    public static enum ItemVariant
    {
        EDITABLE_TEXT,
        // Part before decimal point:
        EDITABLE_NUMBER_INT,
        // Part after decimal point:
        EDITABLE_NUMBER_FRAC,
        EDITABLE_BOOLEAN,

        EDITABLE_DAY,
        EDITABLE_MONTH,
        EDITABLE_YEAR,
        EDITABLE_HOUR,
        EDITABLE_MINUTE,
        EDITABLE_SECOND,
        EDITABLE_OFFSET_HOUR,
        EDITABLE_OFFSET_MINUTE,
        EDITABLE_ZONEID,
        TIMEZONE_PLUS_MINUS,

        TAG_NAME,

        DIVIDER,
        NUMBER_DOT,
        EXPANDABLE_LIST_DIVIDER, // Comma in a list that lets you add and remove items
        EXPANDABLE_LIST_END, // Closing square bracket on a list that lets you add more.
        EMPTY_LIST_PROMPT; // Empty item saying List as a prompt, where lists are optional

        public boolean validCharacterForItem(String before, int c)
        {
            switch (this)
            {
                case EMPTY_LIST_PROMPT:
                    return c == '[';
                case NUMBER_DOT:
                    return c == '.' && before.isEmpty();
                case TAG_NAME:
                    return Character.isAlphabetic(c) || c == '_' || (!before.isEmpty() && c >= '0' && c <= '9');
                case EDITABLE_TEXT:
                    return c != '\"' || endsWithEscapeStart(before);
                case EDITABLE_NUMBER_INT:
                    return (before.isEmpty() && c == '-') || (c >= '0' && c <= '9');
                case EDITABLE_NUMBER_FRAC:
                    return (c >= '0' && c <= '9');
                case TIMEZONE_PLUS_MINUS:
                    return before.isEmpty() && (c == '+' || c == '-');
                case EDITABLE_HOUR:
                case EDITABLE_MINUTE:
                case EDITABLE_OFFSET_HOUR:
                case EDITABLE_OFFSET_MINUTE:
                    return (c >= '0' && c <= '9');
                case EDITABLE_SECOND:
                    return (c >= '0' && c <= '9') || c == '.';
                case EDITABLE_ZONEID:
                    return ZoneId.getAvailableZoneIds().stream().collect(Collectors.joining()).contains(new String(new int[] {c}, 0, 1))
                        || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == ':';
                // Day, Month Year allow month names in any of them:
                default:
                    return (c >= '0' && c <= '9') || Character.isAlphabetic(c);
            }
        }

        private static boolean endsWithEscapeStart(String before)
        {
            boolean nextEscaped = false;
            for (int cp : before.codePoints().toArray())
            {
                if (!nextEscaped && cp == '^')
                    nextEscaped = true;
                else if (nextEscaped)
                    nextEscaped = false;
            }
            return nextEscaped;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static class Item
    {
        // Basically a walk down the tree.  First is highest parent, last is lowest parent.
        // They nest because if you have say a tagged inside a tagged, the innermost has both tagged components
        // as a parent (outermost first, innermost second)
        private final ImmutableList<Component<?>> parent;
        private final String content;
        private final ItemVariant itemVariant;
        private final @Localized String prompt;
        private final List<Integer> otherEntryOptions = new ArrayList<>();
        private final ArrayList<String> styleClasses = new ArrayList<>();

        // Divider:
        public Item(ImmutableList<Component<?>> parent, String divider, String... otherEntryOptions)
        {
            this(parent, divider, ItemVariant.DIVIDER, "");
            this.otherEntryOptions.addAll(Arrays.stream(otherEntryOptions).flatMapToInt(String::codePoints).boxed().collect(Collectors.toList()));
        }

        public Item(ImmutableList<Component<?>> parent, String content, ItemVariant itemVariant, @Localized String prompt)
        {
            this.parent = parent;
            this.content = content;
            this.itemVariant = itemVariant;
            this.prompt = prompt;
        }

        public boolean isValidDividerEntry(int codepoint)
        {
            return getType() == ItemVariant.DIVIDER && content.codePointAt(0) == codepoint || otherEntryOptions.contains(codepoint);
        }

        public StyledText<Collection<String>> toStyledText()
        {
            return new StyledText<>(getScreenText(), toStyles());
        }

        public Collection<String> toStyles()
        {
            return styleClasses;
        }

        public int getLength()
        {
            return content.length();
        }

        public int getScreenLength()
        {
            return content.isEmpty() ? prompt.length() : content.length();
        }

        public String getScreenText()
        {
            return content.isEmpty() ? prompt : content;
        }

        public String getValue()
        {
            return content;
        }

        public ItemVariant getType()
        {
            return itemVariant;
        }

        public ImmutableList<Component<?>> getParents()
        {
            return parent;
        }

        // Cuts the content in the given range, leaving any content that is before or after
        public Item withCutContent(int startIncl, int endExcl)
        {
            return new Item(parent, content.substring(0, startIncl) + content.substring(endExcl), itemVariant, prompt);
        }

        public Item withInsertAt(int insertBefore, String newContent)
        {
            // Shouldn't be needed, but code defensively:
            insertBefore = Math.min(insertBefore, content.length());
            return new Item(parent, content.substring(0, insertBefore) + newContent + content.substring(insertBefore), itemVariant, prompt);
        }

        public Item replaceContent(String newContent)
        {
            return new Item(parent, newContent, itemVariant, prompt).withStyleClasses(this.styleClasses.toArray(new String[0]));
        }

        public Item withStyleClasses(String... styleClasses)
        {
            this.styleClasses.addAll(Arrays.asList(styleClasses));
            return this;
        }
    }

    public static class Suggestion
    {
        // Item index, offset within item
        public final Pair<Integer, Integer> startIndexIncl;
        public final Pair<Integer, Integer> endIndexIncl;
        public final String suggestion;

        public Suggestion(Item item, String suggestion)
        {
            this(new Pair<>(0, 0), new Pair<>(0, item.getLength()), suggestion);
        }

        public Suggestion(Pair<Integer, Integer> startIndexIncl, Pair<Integer, Integer> endIndexIncl, String suggestion)
        {
            this.startIndexIncl = startIndexIncl;
            this.endIndexIncl = endIndexIncl;
            this.suggestion = suggestion;
        }

        public Suggestion offsetByNumItems(int amount)
        {
            return new Suggestion(startIndexIncl.mapFirst(i -> i + amount), endIndexIncl.mapFirst(i -> i + amount), suggestion);
        }

        public int getItemsLength()
        {
            return endIndexIncl.getFirst() - startIndexIncl.getFirst();
        }
    }

    // Field, index within field
    private Pair<Integer, Integer> plainToStructured(int position)
    {
        for (int i = 0; i < curValue.size(); i++)
        {
            Item item = curValue.get(i);
            // If something falls on the borderline between a divider and a non-divider, we prefer the non-divider.
            // If the non-divider is first, it will already have been returned.  If the divider is first, we wait
            // until the next one:
            if (position < item.getScreenLength() || (item.itemVariant != ItemVariant.DIVIDER && position == item.getScreenLength()))
                return new Pair<>(i, position);
            position -= item.getScreenLength();
        }
        // Return end as a fallback:
        return new Pair<>(curValue.size() - 1, curValue.get(curValue.size() - 1).getScreenLength());
    }

    // package-visible
    int structuredToPlain(Pair<Integer, Integer> structured)
    {
        int pos = 0;
        for (int i = 0; i < structured.getFirst(); i++)
        {
            pos += curValue.get(i).getScreenLength();
        }
        return pos + structured.getSecond();
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void previousChar(SelectionPolicy selectionPolicy)
    {
        if (getCaretPosition() != 0)
        {
            int prev = possibleCaretPositions.previousSetBit(getCaretPosition() - 1);
            if (prev != -1)
            {
                moveTo(prev, selectionPolicy);
            }
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void nextChar(SelectionPolicy selectionPolicy)
    {
        System.err.println("Policy: " + selectionPolicy);
        int next = possibleCaretPositions.nextSetBit(getCaretPosition() + 1);
        if (next != -1)
        {
            moveTo(next, selectionPolicy);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void wordBreaksBackwards(int n, SelectionPolicy selectionPolicy)
    {
        // So it seems that RichTextFX goes back two if the neighbouring character is whitespace, but that's not the behaviour we want
        // So just override the count to be 1:
        n = 1;

        if (getCaretPosition() != 0)
        {
            int prev = possibleCaretWordPositions.previousSetBit(getCaretPosition() - 1);
            if (prev != -1)
            {
                moveTo(prev, selectionPolicy);
            }
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void wordBreaksForwards(int n, SelectionPolicy selectionPolicy)
    {
        // So it seems that RichTextFX goes back two if the neighbouring character is whitespace, but that's not the behaviour we want
        // So just override the count to be 1:
        n = 1;

        int next = possibleCaretWordPositions.nextSetBit(getCaretPosition() + 1);
        if (next != -1)
        {
            moveTo(next, selectionPolicy);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public CharacterHit hit(double x, double y)
    {
        CharacterHit hit = super.hit(x, y);
        // Character index may be missing if for example they clicked beyond extents of field.

        // We need to find the structured index, and if it's in a prompt then clamp to one side or other
        return makeHit(Utility.mapOptionalInt(hit.getCharacterIndex(), p -> structuredToPlain(clamp(plainToStructured(p)))), structuredToPlain(clamp(plainToStructured(hit.getInsertionIndex()))));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void lineEnd(SelectionPolicy policy)
    {
        int pos = possibleCaretPositions.length() - 1;
        if (pos != -1)
        {
            moveTo(pos, policy);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void lineStart(SelectionPolicy policy)
    {
        int pos = possibleCaretPositions.nextSetBit(0);
        if (pos != -1)
        {
            moveTo(pos, policy);
        }
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void selectAll()
    {
        lineStart(SelectionPolicy.CLEAR);
        lineEnd(SelectionPolicy.EXTEND);
    }

    private CharacterHit makeHit(OptionalInt charIdx, int insertionIdx)
    {
        // TODO this isn't quite right if the positions get split over a prompt; is that an issue?
        return charIdx.isPresent() ? CharacterHit.leadingHalfOf(insertionIdx) : CharacterHit.insertionAt(insertionIdx);
    }

    // If position is in a prompt, move it to nearest side
    private Pair<Integer, Integer> clamp(Pair<Integer, Integer> pos)
    {
        if (pos.getSecond() >= curValue.get(pos.getFirst()).getLength())
            return new Pair<>(pos.getFirst(), curValue.get(pos.getFirst()).getLength());
        else
            return pos;
    }

    public static class EditorKit<T>
    {
        private final Component<T> contentComponent;
        private final FXPlatformRunnable relinquishFocus;
        private @Nullable T completedValue;
        private final @Nullable FXPlatformConsumer<Pair<String, T>> store;
        private @Nullable State<T> lastValidValue;
        public final ImmutableList<String> stfStyles;
        private @Nullable StructuredTextField curField;

        public EditorKit(Component<T> contentComponent, @Nullable FXPlatformConsumer<Pair<String, T>> store, FXPlatformRunnable relinquishFocus, ImmutableList<String> stfStyles)
        {
            this.contentComponent = contentComponent;
            this.store = store;
            this.relinquishFocus = relinquishFocus;
            this.stfStyles = stfStyles;
        }

        private @Nullable State<T> captureState(List<Item> curValue, StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> document)
        {
            if (curValue != null && completedValue != null)
            {
                @NonNull T val = completedValue;
                return new State<>(curValue, ReadOnlyStyledDocument.from(document), val);
            }
            else
                return null;
        }

        public void storeValue(T v, StructuredTextField stf)
        {
            completedValue = v;
            if (store != null)
                store.consume(new Pair<>(stf.getText(), v));
            lastValidValue = captureState(stf.curValue, stf.getDocument());
            curField = stf;
        }

        public void relinquishFocus()
        {
            relinquishFocus.run();
        }

        public @Nullable T getLastCompletedValue()
        {
            return completedValue;
        }

        public @Nullable StructuredTextField getCurField()
        {
            return curField;
        }
        
        public boolean isEditable()
        {
            return store != null;
        }

        /**
         * If the top-level component belongs to the given class, return it, else return null.
         */
        public <C extends Component<?>> @Nullable C getComponent(Class<C> targetClass)
        {
            if (targetClass.isInstance(contentComponent))
                return targetClass.cast(contentComponent);
            else
                return null;
        }

        public @Nullable CaretPositionMapper focusChanged(boolean focused)
        {
            return contentComponent.focusChanged(focused);
        }

        public void setField(@Nullable StructuredTextField structuredTextField)
        {
            curField = structuredTextField;
        }
    }

}

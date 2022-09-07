package xyz.columnal.gui.dtf;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.dtf.Document.TrackedPosition.Bias;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformRunnable;
import xyz.columnal.utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class Document
{
    private final List<DocumentListener> listeners = new ArrayList<>();

    public static class TrackedPosition
    {
        public static enum Bias {BACKWARD, FORWARD};
        
        private final Bias bias;
        private final @Nullable FXPlatformRunnable onChange;
        private int position;

        TrackedPosition(int position, Bias bias, @Nullable FXPlatformRunnable onChange)
        {
            this.bias = bias;
            this.position = position;
            this.onChange = onChange;
        }

        public Bias getBias()
        {
            return bias;
        }

        @SuppressWarnings("units") // To convert to canonical location
        public @CanonicalLocation int getPosition()
        {
            return position;
        }

        public void moveBy(int amount)
        {
            position += amount;
            if (onChange != null)
                onChange.run();
        }

        public void moveTo(int newPos)
        {
            this.position = newPos;
            if (onChange != null)
                onChange.run();
        }
    }
    
    // Package-protected
    abstract Stream<Pair<Set<String>, String>> getStyledSpans(boolean focused);

    // Package-protected
    abstract void replaceText(int startPosIncl, int endPosExcl, String text);

    // Package-protected
    abstract TrackedPosition trackPosition(int pos, Bias bias, @Nullable FXPlatformRunnable onChange);

    public abstract int getLength();

    abstract boolean isEditable();

    public abstract String getText();
    
    abstract boolean hasError();

    abstract void focusChanged(boolean focused);
    
    // Bit weird in Document, but it's easiest that way
    void defocus(KeyCode defocusCause)
    {
    }

    int mapCaretPos(int pos)
    {
        return pos;
    }
    
    static interface DocumentListener
    {
        @OnThread(Tag.FXPlatform)
        public void documentChanged(Document document);
    }
    
    final void addListener(DocumentListener listener)
    {
        listeners.add(listener);
    }
    
    final void removeListener(DocumentListener listener)
    {
        listeners.removeAll(ImmutableList.of(listener));
    }
    
    protected void notifyListeners()
    {
        for (DocumentListener listener : listeners)
        {
            listener.documentChanged(this);
        }
    }
    
    public abstract void setAndSave(String content);
    
    // Value to undo to, if applicable.  null if n/a
    public @Nullable String getUndo()
    {
        return null;
    }
    
    public ImmutableList<MenuItem> getAdditionalMenuItems(boolean focused)
    {
        return ImmutableList.of();
    }
}

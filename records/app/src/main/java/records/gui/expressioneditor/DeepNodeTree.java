package records.gui.expressioneditor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import utility.gui.FXUtility;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class DeepNodeTree
{
    private final ObservableList<Node> nodes = FXCollections.observableArrayList();
    protected final SimpleBooleanProperty atomicEdit = new SimpleBooleanProperty(false);
    // The boolean value is only used during updateListeners, will be true other times
    private final IdentityHashMap<EEDisplayNode, Boolean> listeningTo = new IdentityHashMap<>();
    private final ListChangeListener<Node> childrenNodeListener;

    @SuppressWarnings("initialization")
    protected DeepNodeTree()
    {
        this.childrenNodeListener = c ->
        {
            updateNodes();
        };
        FXUtility.addChangeListenerPlatformNN(atomicEdit, inProgress -> {
            if (!inProgress)
            {
                // At end of edit:
                updateNodes();
                updateListeners();
            }
        });
    }

    @SuppressWarnings("initialization") // Can't prove calculateNodes is safe to call here
    protected final void updateNodes(@UnknownInitialization(DeepNodeTree.class) DeepNodeTree this)
    {
        if (atomicEdit.get())
            return;

        nodes().setAll(calculateNodes().collect(Collectors.toList()));

        updateDisplay();
    }

    public final ObservableList<Node> nodes()
    {
        return nodes;
    }

    // Does not have to be in display order:
    protected abstract Stream<EEDisplayNode> calculateChildren();

    // Must be in display order:
    protected abstract Stream<Node> calculateNodes();

    protected abstract void updateDisplay();

    protected final void updateListeners()
    {
        if (atomicEdit.get())
            return;

        // Make them all as old (false)
        listeningTo.replaceAll((e, b) -> false);
        // Merge new ones:

        calculateChildren().forEach(child -> {
            // No need to listen again if already present as we're already listening
            if (listeningTo.get(child) == null)
                child.nodes().addListener(childrenNodeListener);
            listeningTo.put(child, true);
        });
        // Stop listening to old:
        for (Iterator<Entry<EEDisplayNode, Boolean>> iterator = listeningTo.entrySet().iterator(); iterator.hasNext(); )
        {
            Entry<EEDisplayNode, Boolean> e = iterator.next();
            if (e.getValue() == false)
            {
                e.getKey().nodes().removeListener(childrenNodeListener);
                iterator.remove();
            }
        }
    }

    protected void listenToNodeRelevantList(ObservableList<?> children)
    {
        FXUtility.listen(children,c ->
        {
            updateNodes();
            updateListeners();
        });

        updateNodes();
        updateListeners();
    }

}

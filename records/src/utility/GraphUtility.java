package utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Created by neil on 21/11/2016.
 */
public class GraphUtility
{
    /**
     * Collapses a directed acyclic graph into a linear list of nodes, such that all edges
     * point forwards in the list.  The nodes in putAsLateAsPossible are put as late in the list
     * as possible (if there are multiple orderings available).
     *
     * @param nodes The full set of nodes in the graph, which will be returned, but maybe in a different order
     * @param originalEdges A map from a node to its list of incoming edges.  Nodes with no incoming may be absent or map to an empty list.
     * @param putAsLateAsPossible See above
     * @param <T> The node type.  Nodes are compared using .equals
     * @return The flattened ordered list of nodes.
     */
    public static <T> List<T> lineariseDAG(Collection<T> nodes, Map<T, ? extends Collection<T>> originalEdges, Collection<T> putAsLateAsPossible)
    {
        // Kahn's algorithm, from wikipedia:
        //   L ← Empty list that will contain the sorted elements
        //   S ← Set of all nodes with no incoming edges
        //   while S is non-empty do
        //     remove a node n from S
        //     add n to tail of L
        //     for each node m with an edge e from n to m do
        //        remove edge e from the graph
        //        if m has no other incoming edges then
        //           insert m into S

        // We don't have any empty lists in the map:
        Map<T, List<T>> remainingEdges = new HashMap<>();
        for (Entry<T, ? extends Collection<T>> origEdge : originalEdges.entrySet())
        {
            if (!origEdge.getValue().isEmpty())
                remainingEdges.put(origEdge.getKey(), new ArrayList<>(origEdge.getValue()));
        }

        List<T> l = new ArrayList<T>();
        List<T> s = new ArrayList<T>(nodes);
        for (Iterator<T> iterator = s.iterator(); iterator.hasNext(); )
        {
            T t = iterator.next();
            Collection<T> incoming = remainingEdges.get(t);
            if (incoming != null) // List is not empty given above list, so no need to check size
                iterator.remove(); // Has an incoming edge; shouldn't be in s.
        }

        while (!s.isEmpty())
        {
            T next = null;
            // Faster to remove from end:
            for (int i = s.size() - 1; i >= 0; i--)
            {
                if (!putAsLateAsPossible.contains(s.get(i)))
                {
                    next = s.remove(i);
                    break;
                }
            }
            // Have to take a late one:
            if (next == null)
                next = s.remove(s.size() - 1); // Faster to remove from end
            l.add(next);
            for (Iterator<Entry<T, List<T>>> iterator = remainingEdges.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<T, List<T>> incoming = iterator.next();
                incoming.getValue().remove(next);
                if (incoming.getValue().isEmpty())
                {
                    s.add(incoming.getKey());
                    iterator.remove();
                }
            }
        }

        return l;
    }
}

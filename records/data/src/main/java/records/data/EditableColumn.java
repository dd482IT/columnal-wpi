package records.data;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;

import java.util.List;

/**
 * Created by neil on 29/05/2017.
 */
public abstract class EditableColumn extends Column
{
    protected EditableColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
    }

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable insertRows(int index, int count) throws InternalException, UserException;

    // Returns a revert operation
    @OnThread(Tag.Simulation)
    public abstract SimulationRunnable removeRows(int index, int count) throws InternalException, UserException;
}

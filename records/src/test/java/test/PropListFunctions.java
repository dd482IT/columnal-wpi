package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.runner.RunWith;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.function.Absolute;
import records.transformations.function.Count;
import records.transformations.function.FunctionDefinition;
import records.transformations.function.FunctionInstance;
import records.transformations.function.Mean;
import records.transformations.function.Round;
import records.transformations.function.Sum;
import test.gen.GenNumber;
import test.gen.GenNumbers;
import test.gen.GenUnit;
import test.gen.GenValueList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by neil on 13/12/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropListFunctions
{
    private @MonotonicNonNull UnitManager mgr;
    @Before
    public void init() throws UserException, InternalException
    {
        mgr = new UnitManager();
    }


    @Property
    @OnThread(Tag.Simulation)
    public void propCount(@From(GenValueList.class) GenValueList.ListAndType src) throws Throwable
    {
        if (mgr == null)
            throw new RuntimeException();
        Count function = new Count();
        @Nullable Pair<FunctionInstance, DataType> checked = function.typeCheck(Collections.emptyList(), src.type, s -> {}, mgr);
        if (checked == null)
        {
            fail("Type check failure");
        }
        else
        {
            assertEquals(DataType.NUMBER, checked.getSecond());
            @Value Object actual = checked.getFirst().getValue(0, Utility.value(src.list));
            assertEquals(src.list.size(), actual);
        }
    }

}

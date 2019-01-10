package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sosy_lab.common.rationals.Rational;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.typeExp.units.MutUnitVar;
import records.typeExp.units.UnitExp;
import test.gen.GenUnit;
import utility.ExConsumer;
import utility.ExRunnable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Created by neil on 24/01/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropUnit
{
    private final UnitManager mgr;
    {
        try
        {
            mgr = new UnitManager();
        }
        catch (UserException | InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    @Property
    public void propUnits(@From(GenUnit.class) Unit a, @From(GenUnit.class) Unit b) throws UserException, InternalException
    {
        assertEquals(a.equals(b), b.equals(a));
        assertEquals(a.canScaleTo(b, mgr), b.canScaleTo(a, mgr).map(Rational::reciprocal));
        assertEquals(a, a.reciprocal().reciprocal());
        assertEquals(b, b.reciprocal().reciprocal());
        assertEquals(a.divideBy(b), b.divideBy(a).reciprocal());
        assertEquals(a.times(b), a.divideBy(b.reciprocal()));
        assertEquals(a, a.divideBy(b).times(b));
        assertEquals(a, a.times(b).divideBy(b));
        for (int i = 1; i < 10; i++)
            assertEquals(a, a.raisedTo(i).rootedBy(i));
    }
    
    @Test
    public void testTypecheckUnitTimes() throws UserException, InternalException
    {
        withMut(u -> {
            unify(unitExp("m"), times(u, unitExp("m/s")));
            assertEquals(unit("s"), u.toConcreteUnit());
        });

        withMut(u -> {
            unify(unitExp("m"), times(u, UnitExp.SCALAR));
            assertEquals(unit("m"), u.toConcreteUnit());
        });

        withMut(u -> {
            unify(u.raisedTo(4), times(unitExp("m^3/s"), unitExp("m/s^3")));
            assertEquals(unit("m/s"), u.toConcreteUnit());
        });
        withMut(u -> {
            unify(u.raisedTo(2), times(unitExp("m^3/s"), unitExp("m/s^3")));
            assertEquals(unit("m^2/s^2"), u.toConcreteUnit());
        });
        withMut(u -> withMut(u3 -> {
            // Test that an ambiguous resolution, followed by clarifier, gives right result:
            unify(u.times(u3.raisedTo(3)), times(unitExp("m^3/s"), unitExp("m/s^3")));
            unify(u, unitExp("m/s"));
            assertEquals(unit("m/s"), u.toConcreteUnit());
            assertEquals(unit("m/s"), u3.toConcreteUnit());
        }));
        withMut(u -> withMut(u3 -> {
            // Test that an ambiguous resolution, followed by clarifier, gives right result:
            unify(u.times(u3.raisedTo(4)), times(unitExp("m^3/s"), unitExp("m/s^3")));
            unify(u, unitExp("m^4"));
            assertEquals(unit("m^4"), u.toConcreteUnit());
            assertEquals(unit("s^-1"), u3.toConcreteUnit());
        }));
        withMut(u -> withMut(u3 -> {
            // Test that an ambiguous resolution, followed by clarifier, gives right result:
            unify(u.times(u3), times(unitExp("m^3/s"), unitExp("m/s^3")));
            unify(u, unitExp("m/s"));
            assertEquals(unit("m/s"), u.toConcreteUnit());
            assertEquals(unit("m^3/s^3"), u3.toConcreteUnit());
        }));
        
        failure(() -> unify(unitExp("m"), unitExp("s")));
    }
    
    private void failure(ExRunnable item)
    {
        try
        {
            item.run();
            fail("Expected failure but succeeded");
        }
        catch (AssertionError | InternalException | UserException e)
        {
            return;
        }
    }

    private void withMut(ExConsumer<UnitExp> with) throws InternalException, UserException
    {
        with.accept(new UnitExp(new MutUnitVar()));
    }

    private UnitExp times(UnitExp... units)
    {
        UnitExp u = units[0];
        for (int i = 1; i < units.length; i++)
        {
            u = u.times(units[i]);
        }
        return u;
    }
    
    private void unify(UnitExp... units)
    {
        for (int i = 1; i < units.length; i++)
        {
            assertNotNull("Unifying " + units[0] + " with " + units[i], units[0].unifyWith(units[i]));
        }
    }

    private UnitExp unitExp(String unit) throws InternalException, UserException
    {
        return UnitExp.fromConcrete(unit(unit));
    }

    private Unit unit(String unit) throws UserException, InternalException
    {
        return mgr.loadUse(unit);
    }
}
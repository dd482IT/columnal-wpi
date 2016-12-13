package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import utility.Utility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 13/12/2016.
 */
public class GenNumber extends Generator<Number>
{
    public GenNumber()
    {
        super(Number.class);
    }

    @Override
    public Number generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Number n = Utility.parseNumber(new GenNumberAsString().generate(sourceOfRandomness, generationStatus));
        List<Number> rets = new ArrayList<>();
        rets.add(n);
        if (n.doubleValue() == (double)n.intValue())
        {
            // If it fits in smaller, we may randomly choose to use smaller:
            rets.add(BigInteger.valueOf(n.intValue()));
            if ((long) n.intValue() == n.longValue())
                rets.add(n.intValue());
            if ((long) n.shortValue() == n.longValue())
                rets.add(n.shortValue());
            if ((long) n.byteValue() == n.longValue())
                rets.add(n.byteValue());
        }
        return sourceOfRandomness.choose(rets);
    }
}

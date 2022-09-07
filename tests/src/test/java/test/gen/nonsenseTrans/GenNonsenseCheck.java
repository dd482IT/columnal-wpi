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

package test.gen.nonsenseTrans;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableId;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.Check;
import xyz.columnal.transformations.Check.CheckType;
import xyz.columnal.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.TestUtil.Transformation_Mgr;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

/**
 * Created by neil on 27/11/2016.
 */
public class GenNonsenseCheck extends Generator<Transformation_Mgr>
{
    public GenNonsenseCheck()
    {
        super(Transformation_Mgr.class);
    }

    @Override
    @OnThread(value = Tag.Simulation, ignoreParent = true)
    public Transformation_Mgr generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        Pair<TableId, TableId> ids = TestUtil.generateTableIdPair(sourceOfRandomness);
        try
        {
            DummyManager mgr = TestUtil.managerWithTestTypes().getFirst();
            GenNonsenseExpression genNonsenseExpression = new GenNonsenseExpression();
            genNonsenseExpression.setTableManager(mgr);
            Expression nonsenseExpression = genNonsenseExpression.generate(sourceOfRandomness, generationStatus);
            return new Transformation_Mgr(mgr, new Check(mgr, new InitialLoadDetails(ids.getFirst(), null, null, null), ids.getSecond(), CheckType.values()[sourceOfRandomness.nextInt(CheckType.values().length)], nonsenseExpression));
        }
        catch (InternalException e)
        {
            throw new RuntimeException(e);
        }
    }
}

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

package test.gen.backwards;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.Expression;

@FunctionalInterface
public interface ExpressionMaker
{
    public Expression make() throws InternalException, UserException;
    
    public default int getBias()
    {
        return 1;
    }
    
    public default ExpressionMaker withBias(int bias)
    {
        ExpressionMaker orig = this;
        return new ExpressionMaker()
        {
            @Override
            public Expression make() throws InternalException, UserException
            {
                return orig.make();
            }

            @Override
            public int getBias()
            {
                return bias;
            }
        };
    }
}

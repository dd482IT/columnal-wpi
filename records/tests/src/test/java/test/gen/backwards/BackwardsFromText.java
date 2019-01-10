package test.gen.backwards;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.CallExpression;
import records.transformations.expression.StringLiteral;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.type.TypeExpression;

import java.util.List;

@SuppressWarnings("recorded")
public class BackwardsFromText extends BackwardsProvider
{
    public BackwardsFromText(SourceOfRandomness r, RequestBackwardsExpression parent)
    {
        super(r, parent);
    }

    @Override
    public List<ExpressionMaker> terminals(DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {
        String val = DataTypeUtility.valueToString(targetType, targetValue, null);
        return ImmutableList.of(
            () -> new CallExpression(parent.getTypeManager().getUnitManager(), "from text to",
                new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                new StringLiteral(val)    
            )
        );
    }

    @Override
    public List<ExpressionMaker> deep(int maxLevels, DataType targetType, @Value Object targetValue) throws InternalException, UserException
    {        
        String val = DataTypeUtility.valueToString(targetType, targetValue, null);
        return ImmutableList.of(
                () -> new CallExpression(parent.getTypeManager().getUnitManager(), "from text to",
                        new TypeLiteralExpression(TypeExpression.fromDataType(targetType)),
                        parent.make(DataType.TEXT, val, maxLevels - 1)
                )
        );
    }
}
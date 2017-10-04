package records.gui.stf;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import records.error.UserException;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class NumberEntry extends TerminalComponent<@Value Number>
{
    public NumberEntry(ImmutableList<Component<?>> parents, @Nullable Number initial)
    {
        super(parents);
        items.add(new Item(getItemParents(), initial == null ? "" : (initial instanceof BigDecimal ? ((BigDecimal) initial).toPlainString() : initial.toString()), ItemVariant.EDITABLE_NUMBER, TranslationUtility.getString("entry.prompt.number")));
    }

    @Override
    public Either<List<ErrorFix>, @Value Number> endEdit(StructuredTextField field)
    {
        try
        {
            return Either.right(DataTypeUtility.value(Utility.parseNumber(getItem(ItemVariant.EDITABLE_NUMBER))));
        }
        catch (UserException e)
        {
            return Either.left(Collections.emptyList());
        }
    }
}

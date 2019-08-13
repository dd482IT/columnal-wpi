package records.gui.dtf.recognisers;

import annotation.qual.Value;
import log.Log;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.PositionedUserException;
import records.data.datatype.DataTypeUtility.StringView;
import records.error.InternalException;
import records.error.UserException;
import records.gui.dtf.Recogniser;
import utility.Either;
import utility.Pair;
import utility.ParseProgress;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemporalRecogniser extends Recogniser<@Value TemporalAccessor>
{
    private final DateTimeType dateTimeType;

    public TemporalRecogniser(DateTimeType dateTimeType)
    {
        this.dateTimeType = dateTimeType;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<@Value TemporalAccessor>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        try
        {
            StringView stringView = new StringView(orig);
            @Value TemporalAccessor temporal = DataTypeUtility.parseTemporalFlexible(new DateTimeInfo(dateTimeType), stringView);
            return success(temporal, stringView.getParseProgress());
        }
        catch (PositionedUserException e)
        {
            return Either.left(new ErrorDetails(e.getStyledMessage(), e.getPosition()));
        }
    }
}

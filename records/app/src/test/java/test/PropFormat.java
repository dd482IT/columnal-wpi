package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.commons.io.FileUtils;
import org.junit.runner.RunWith;
import records.data.DataSource;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat;
import records.importers.GuessFormat.FinalTextFormat;
import records.importers.GuessFormat.Import;
import records.importers.GuessFormat.InitialTextFormat;
import records.importers.GuessFormat.TrimChoice;
import records.importers.TextImporter;
import test.gen.GenFormattedData;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat
{
    @SuppressWarnings("unchecked")
    @Property
    @OnThread(Tag.Simulation)
    public void testGuessFormat(@From(GenFormattedData.class) GenFormattedData.FormatAndData formatAndData, boolean link) throws IOException, UserException, InternalException
    {
        String content = formatAndData.content.stream().collect(Collectors.joining("\n"));
        Import<InitialTextFormat, FinalTextFormat> format = GuessFormat.guessTextFormat(DummyManager.INSTANCE.getTypeManager(), DummyManager.INSTANCE.getUnitManager(), variousCharsets(formatAndData.content, formatAndData.format.initialTextFormat.charset));
        assertEquals("Failure with content: " + content, formatAndData.format, format);
        File tempFile = File.createTempFile("test", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, content, formatAndData.format.initialTextFormat.charset);
        // Can't figure out issue with checker here, came after IntelliJ upgrade!?
        /* TODO restore this
        ChoicePoint<?, DataSource> choicePoint = TextImporter._test_importTextFile(new DummyManager(), tempFile); //, link);
        DataSource ds = TestUtil.pick(choicePoint, picks);
        assertEquals("Right column length", formatAndData.loadedContent.size(), ds.getData().getLength());
        for (int i = 0; i < formatAndData.loadedContent.size(); i++)
        {
            assertEquals("Right row length for row " + i + " (+" + formatAndData.format.headerRows + "):\n" + formatAndData.content.get(i + formatAndData.format.headerRows) + "\n" + format + "\n" + Utility.listToString(formatAndData.loadedContent.get(i)) + " guessed: " + "", ds.getData().getColumns().size(), formatAndData.loadedContent.get(i).size());
            for (int c = 0; c < ds.getData().getColumns().size(); c++)
            {
                @Value Object expected = formatAndData.loadedContent.get(i).get(c);
                @Value Object loaded = ds.getData().getColumns().get(c).getType().getCollapsed(i);
                assertEquals("Column " + c + " expected: " + expected + " was " + loaded + " from row " + formatAndData.content.get(i + 1), 0, Utility.compareValues(expected, loaded));
            }
        }
        */
    }

    private Map<Charset, List<String>> variousCharsets(List<String> content, Charset actual)
    {
        Map<Charset, List<String>> m = new HashMap<>();
        String joined = content.stream().collect(Collectors.joining("\n"));
        for (Charset c : Arrays.asList(Charset.forName("UTF-8"), Charset.forName("ISO-8859-1"), Charset.forName("UTF-16")))
        {
            m.put(c, Arrays.asList(new String(joined.getBytes(actual), c).split("\n")));
        }

        return m;
    }

}

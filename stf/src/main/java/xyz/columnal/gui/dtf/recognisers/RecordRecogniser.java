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

package xyz.columnal.gui.dtf.recognisers;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.dtf.Recogniser;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.ParseProgress;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class RecordRecogniser extends Recogniser<Record>
{
    private final ImmutableMap<String, Recogniser<? extends Object>> members;

    public RecordRecogniser(ImmutableMap<String, Recogniser<? extends Object>> members)
    {
        this.members = members;
    }

    @Override
    public Either<ErrorDetails, SuccessDetails<Record>> process(ParseProgress orig, boolean immediatelySurroundedByRoundBrackets)
    {
        ParseProgress pp = orig;
        if (!immediatelySurroundedByRoundBrackets)
            pp = pp.consumeNext("(");
        if (pp == null)
            return error("Expected '(' to begin a record", orig.curCharIndex);

        HashMap<String, Recogniser<? extends Object>> remainingFields = new HashMap<String, Recogniser<? extends Object>>(members);
        
        return next(false, !immediatelySurroundedByRoundBrackets, remainingFields, pp, ImmutableMap.builderWithExpectedSize(members.size()), new SortedStringContent(!immediatelySurroundedByRoundBrackets));
    }

    // Recurse down the list of members, processing one and if no error, process next, until no more members and then look for closing bracket:
    private Either<ErrorDetails, SuccessDetails<Record>> next(boolean expectComma, boolean expectClosingBracket, HashMap<String, Recogniser<? extends Object>> remainingMembers, ParseProgress orig, ImmutableMap.Builder<String, SuccessDetails<Object>> soFar, SortedStringContent replTextSoFar)
    {
        ParseProgress pp = orig;
        // If no more members, look for closing bracket:
        if (remainingMembers.isEmpty())
        {
            if (expectClosingBracket)
            {
                pp = pp.consumeNext(")");
            }
            if (pp == null)
                return error("Expected ')' to end record", orig.curCharIndex);
            
            ImmutableMap<String, SuccessDetails<Object>> all = soFar.build();
            return success(RecordMap.immediate(Utility.<String, SuccessDetails<Object>, Object>mapValues(all, s -> s.value)), replTextSoFar.build(), all.values().stream().flatMap(s -> s.styles.stream()).collect(ImmutableList.<StyleSpanInfo>toImmutableList()), pp);
        }

        if (expectComma)
        {
            ParseProgress beforeComma = pp;
            pp = pp.consumeNext(",");
            if (pp == null)
                return error("Expected ',' to separate record fields", beforeComma.curCharIndex);
        }
        
        // Look for field name:
        Pair<String, ParseProgress> fieldName = pp.consumeUpToAndIncluding(":");
        if (fieldName == null)
            return error("Expected field name followed by colon", pp.curCharIndex);
        String name = IdentifierUtility.asExpressionIdentifier(fieldName.getFirst().trim());
        if (name == null)
            return error("Expected field name followed by colon", pp.curCharIndex);
        Recogniser<?> recogniser = remainingMembers.remove(name);
        if (recogniser == null)
            return error("Unknown field name: \"" + name + "\"", pp.curCharIndex);
        pp = fieldName.getSecond();
        
        return recogniser.process(pp, false).<SuccessDetails<Object>>map(s -> s.asObject())
            .<SuccessDetails<Record>>flatMap((SuccessDetails<Object> succ) -> {
                soFar.put(name, succ);
                replTextSoFar.append(name, succ.immediateReplacementText);
                return next(true, expectClosingBracket, remainingMembers, succ.parseProgress, soFar, replTextSoFar);
            });
    }
    
    private static class SortedStringContent
    {
        private final boolean addRoundBrackets;
        private final HashMap<String, String> fieldContent = new HashMap<>();

        public SortedStringContent(boolean addRoundBrackets)
        {
            this.addRoundBrackets = addRoundBrackets;
        }
        
        public void append(String fieldName, String content)
        {
            fieldContent.put(fieldName, content);
        }
        
        public String build()
        {
            return (addRoundBrackets ? "(" : "")
                + fieldContent.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "))
                + (addRoundBrackets ? ")" : "");
        }
    }
}

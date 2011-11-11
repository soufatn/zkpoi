/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.zkoss.poi.hwpf.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.zkoss.poi.hpsf.SummaryInformation;
import org.zkoss.poi.hwpf.HWPFDocument;
import org.zkoss.poi.hwpf.HWPFDocumentCore;
import org.zkoss.poi.hwpf.converter.FontReplacer.Triplet;
import org.zkoss.poi.hwpf.model.FieldsDocumentPart;
import org.zkoss.poi.hwpf.model.ListFormatOverride;
import org.zkoss.poi.hwpf.model.ListTables;
import org.zkoss.poi.hwpf.usermodel.Bookmark;
import org.zkoss.poi.hwpf.usermodel.CharacterRun;
import org.zkoss.poi.hwpf.usermodel.Field;
import org.zkoss.poi.hwpf.usermodel.Notes;
import org.zkoss.poi.hwpf.usermodel.OfficeDrawing;
import org.zkoss.poi.hwpf.usermodel.Paragraph;
import org.zkoss.poi.hwpf.usermodel.Picture;
import org.zkoss.poi.hwpf.usermodel.PictureType;
import org.zkoss.poi.hwpf.usermodel.Range;
import org.zkoss.poi.hwpf.usermodel.Section;
import org.zkoss.poi.hwpf.usermodel.Table;
import org.zkoss.poi.hwpf.usermodel.TableCell;
import org.zkoss.poi.hwpf.usermodel.TableRow;
import org.zkoss.poi.poifs.filesystem.Entry;
import org.zkoss.poi.util.Beta;
import org.zkoss.poi.util.POILogFactory;
import org.zkoss.poi.util.POILogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Beta
public abstract class AbstractWordConverter
{
    private static final class Structure implements Comparable<Structure>
    {
        final int end;
        final int start;
        final Object structure;

        Structure( Bookmark bookmark )
        {
            this.start = bookmark.getStart();
            this.end = bookmark.getEnd();
            this.structure = bookmark;
        }

        Structure( Field field )
        {
            this.start = field.getFieldStartOffset();
            this.end = field.getFieldEndOffset();
            this.structure = field;
        }

        public int compareTo( Structure o )
        {
            return start < o.start ? -1 : start == o.start ? 0 : 1;
        }

        @Override
        public String toString()
        {
            return "Structure [" + start + "; " + end + "): "
                    + structure.toString();
        }
    }

    private static final byte BEL_MARK = 7;

    private static final byte FIELD_BEGIN_MARK = 19;

    private static final byte FIELD_END_MARK = 21;

    private static final byte FIELD_SEPARATOR_MARK = 20;

    private static final POILogger logger = POILogFactory
            .getLogger( AbstractWordConverter.class );

    private static final byte SPECCHAR_AUTONUMBERED_FOOTNOTE_REFERENCE = 2;

    private static final byte SPECCHAR_DRAWN_OBJECT = 8;

    protected static final char UNICODECHAR_NO_BREAK_SPACE = '\u00a0';

    protected static final char UNICODECHAR_NONBREAKING_HYPHEN = '\u2011';

    protected static final char UNICODECHAR_ZERO_WIDTH_SPACE = '\u200b';

    private static void addToStructures( List<Structure> structures,
            Structure structure )
    {
        for ( Iterator<Structure> iterator = structures.iterator(); iterator
                .hasNext(); )
        {
            Structure another = iterator.next();

            if ( another.start <= structure.start
                    && another.end >= structure.start )
            {
                return;
            }

            if ( ( structure.start < another.start && another.start < structure.end )
                    || ( structure.start < another.start && another.end <= structure.end )
                    || ( structure.start <= another.start && another.end < structure.end ) )
            {
                iterator.remove();
                continue;
            }
        }
        structures.add( structure );
    }

    private final Set<Bookmark> bookmarkStack = new LinkedHashSet<Bookmark>();

    private FontReplacer fontReplacer = new DefaultFontReplacer();

    private PicturesManager picturesManager;

    /**
     * Special actions that need to be called after processing complete, like
     * updating stylesheets or building document notes list. Usually they are
     * called once, but it's okay to call them several times.
     */
    protected void afterProcess()
    {
        // by default no such actions needed
    }

    protected Triplet getCharacterRunTriplet( CharacterRun characterRun )
    {
        Triplet original = new Triplet();
        original.bold = characterRun.isBold();
        original.italic = characterRun.isItalic();
        original.fontName = characterRun.getFontName();
        Triplet updated = getFontReplacer().update( original );
        return updated;
    }

    public abstract Document getDocument();

    public FontReplacer getFontReplacer()
    {
        return fontReplacer;
    }

    protected int getNumberColumnsSpanned( int[] tableCellEdges,
            int currentEdgeIndex, TableCell tableCell )
    {
        int nextEdgeIndex = currentEdgeIndex;
        int colSpan = 0;
        int cellRightEdge = tableCell.getLeftEdge() + tableCell.getWidth();
        while ( tableCellEdges[nextEdgeIndex] < cellRightEdge )
        {
            colSpan++;
            nextEdgeIndex++;
        }
        return colSpan;
    }

    protected int getNumberRowsSpanned( Table table,
            final int[] tableCellEdges, int currentRowIndex,
            int currentColumnIndex, TableCell tableCell )
    {
        if ( !tableCell.isFirstVerticallyMerged() )
            return 1;

        final int numRows = table.numRows();

        int count = 1;
        for ( int r1 = currentRowIndex + 1; r1 < numRows; r1++ )
        {
            TableRow nextRow = table.getRow( r1 );
            if ( currentColumnIndex >= nextRow.numCells() )
                break;

            // we need to skip row if he don't have cells at all
            boolean hasCells = false;
            int currentEdgeIndex = 0;
            for ( int c = 0; c < nextRow.numCells(); c++ )
            {
                TableCell nextTableCell = nextRow.getCell( c );
                if ( !nextTableCell.isVerticallyMerged()
                        || nextTableCell.isFirstVerticallyMerged() )
                {
                    int colSpan = getNumberColumnsSpanned( tableCellEdges,
                            currentEdgeIndex, nextTableCell );
                    currentEdgeIndex += colSpan;

                    if ( colSpan != 0 )
                    {
                        hasCells = true;
                        break;
                    }
                }
                else
                {
                    currentEdgeIndex += getNumberColumnsSpanned(
                            tableCellEdges, currentEdgeIndex, nextTableCell );
                }
            }
            if ( !hasCells )
                continue;

            TableCell nextCell = nextRow.getCell( currentColumnIndex );
            if ( !nextCell.isVerticallyMerged()
                    || nextCell.isFirstVerticallyMerged() )
                break;
            count++;
        }
        return count;
    }

    public PicturesManager getPicturesManager()
    {
        return picturesManager;
    }

    protected abstract void outputCharacters( Element block,
            CharacterRun characterRun, String text );

    /**
     * Wrap range into bookmark(s) and process it. All bookmarks have starts
     * equal to range start and ends equal to range end. Usually it's only one
     * bookmark.
     */
    protected abstract void processBookmarks( HWPFDocumentCore wordDocument,
            Element currentBlock, Range range, int currentTableLevel,
            List<Bookmark> rangeBookmarks );

    protected boolean processCharacters( final HWPFDocumentCore wordDocument,
            final int currentTableLevel, final Range range, final Element block )
    {
        if ( range == null )
            return false;

        boolean haveAnyText = false;

        /*
         * In text there can be fields, bookmarks, may be other structures (code
         * below allows extension). Those structures can overlaps, so either we
         * should process char-by-char (slow) or find a correct way to
         * reconstruct the structure of range -- sergey
         */
        List<Structure> structures = new LinkedList<Structure>();
        if ( wordDocument instanceof HWPFDocument )
        {
            final HWPFDocument doc = (HWPFDocument) wordDocument;

            Map<Integer, List<Bookmark>> rangeBookmarks = doc.getBookmarks()
                    .getBookmarksStartedBetween( range.getStartOffset(),
                            range.getEndOffset() );

            if ( rangeBookmarks != null )
            {
                for ( List<Bookmark> lists : rangeBookmarks.values() )
                {
                    for ( Bookmark bookmark : lists )
                    {
                        if ( !bookmarkStack.contains( bookmark ) )
                            addToStructures( structures, new Structure(
                                    bookmark ) );
                    }
                }
            }

            // TODO: dead fields?
            for ( int c = 0; c < range.numCharacterRuns(); c++ )
            {
                CharacterRun characterRun = range.getCharacterRun( c );
                if ( characterRun == null )
                    throw new AssertionError();
                Field aliveField = ( (HWPFDocument) wordDocument ).getFields()
                        .getFieldByStartOffset( FieldsDocumentPart.MAIN,
                                characterRun.getStartOffset() );
                if ( aliveField != null )
                {
                    addToStructures( structures, new Structure( aliveField ) );
                }
            }
        }

        structures = new ArrayList<Structure>( structures );
        Collections.sort( structures );

        int previous = range.getStartOffset();
        for ( Structure structure : structures )
        {
            if ( structure.start != previous )
            {
                Range subrange = new Range( previous, structure.start, range )
                {
                    @Override
                    public String toString()
                    {
                        return "BetweenStructuresSubrange " + super.toString();
                    }
                };
                processCharacters( wordDocument, currentTableLevel, subrange,
                        block );
            }

            if ( structure.structure instanceof Bookmark )
            {
                // other bookmarks with same boundaries
                List<Bookmark> bookmarks = new LinkedList<Bookmark>();
                for ( Bookmark bookmark : ( (HWPFDocument) wordDocument )
                        .getBookmarks()
                        .getBookmarksStartedBetween( structure.start,
                                structure.start + 1 ).values().iterator()
                        .next() )
                {
                    if ( bookmark.getStart() == structure.start
                            && bookmark.getEnd() == structure.end )
                    {
                        bookmarks.add( bookmark );
                    }
                }

                bookmarkStack.addAll( bookmarks );
                try
                {
                    int end = Math.min( range.getEndOffset(), structure.end );
                    Range subrange = new Range( structure.start, end, range )
                    {
                        @Override
                        public String toString()
                        {
                            return "BookmarksSubrange " + super.toString();
                        }
                    };

                    processBookmarks( wordDocument, block, subrange,
                            currentTableLevel, bookmarks );
                }
                finally
                {
                    bookmarkStack.removeAll( bookmarks );
                }
            }
            else if ( structure.structure instanceof Field )
            {
                Field field = (Field) structure.structure;
                processField( (HWPFDocument) wordDocument, range,
                        currentTableLevel, field, block );
            }
            else
            {
                throw new UnsupportedOperationException( "NYI: "
                        + structure.structure.getClass() );
            }

            previous = Math.min( range.getEndOffset(), structure.end );
        }

        if ( previous != range.getStartOffset() )
        {
            if ( previous > range.getEndOffset() )
            {
                logger.log( POILogger.WARN, "Latest structure in ", range,
                        " ended at #" + previous, " after range boundaries [",
                        range.getStartOffset() + "; " + range.getEndOffset(),
                        ")" );
                return true;
            }

            if ( previous < range.getEndOffset() )
            {
                Range subrange = new Range( previous, range.getEndOffset(),
                        range )
                {
                    @Override
                    public String toString()
                    {
                        return "AfterStructureSubrange " + super.toString();
                    }
                };
                processCharacters( wordDocument, currentTableLevel, subrange,
                        block );
            }
            return true;
        }

        for ( int c = 0; c < range.numCharacterRuns(); c++ )
        {
            CharacterRun characterRun = range.getCharacterRun( c );

            if ( characterRun == null )
                throw new AssertionError();

            if ( wordDocument instanceof HWPFDocument
                    && ( (HWPFDocument) wordDocument ).getPicturesTable()
                            .hasPicture( characterRun ) )
            {
                HWPFDocument newFormat = (HWPFDocument) wordDocument;
                Picture picture = newFormat.getPicturesTable().extractPicture(
                        characterRun, true );

                processImage( block, characterRun.text().charAt( 0 ) == 0x01,
                        picture );
                continue;
            }

            String text = characterRun.text();
            if ( text.getBytes().length == 0 )
                continue;

            if ( characterRun.isSpecialCharacter() )
            {
                if ( text.charAt( 0 ) == SPECCHAR_AUTONUMBERED_FOOTNOTE_REFERENCE
                        && ( wordDocument instanceof HWPFDocument ) )
                {
                    HWPFDocument doc = (HWPFDocument) wordDocument;
                    processNoteAnchor( doc, characterRun, block );
                    continue;
                }
                if ( text.charAt( 0 ) == SPECCHAR_DRAWN_OBJECT
                        && ( wordDocument instanceof HWPFDocument ) )
                {
                    HWPFDocument doc = (HWPFDocument) wordDocument;
                    processDrawnObject( doc, characterRun, block );
                    continue;
                }
                if ( characterRun.isOle2()
                        && ( wordDocument instanceof HWPFDocument ) )
                {
                    HWPFDocument doc = (HWPFDocument) wordDocument;
                    processOle2( doc, characterRun, block );
                    continue;
                }
            }

            if ( text.getBytes()[0] == FIELD_BEGIN_MARK )
            {
                if ( wordDocument instanceof HWPFDocument )
                {
                    Field aliveField = ( (HWPFDocument) wordDocument )
                            .getFields().getFieldByStartOffset(
                                    FieldsDocumentPart.MAIN,
                                    characterRun.getStartOffset() );
                    if ( aliveField != null )
                    {
                        processField( ( (HWPFDocument) wordDocument ), range,
                                currentTableLevel, aliveField, block );

                        int continueAfter = aliveField.getFieldEndOffset();
                        while ( c < range.numCharacterRuns()
                                && range.getCharacterRun( c ).getEndOffset() <= continueAfter )
                            c++;

                        if ( c < range.numCharacterRuns() )
                            c--;

                        continue;
                    }
                }

                int skipTo = tryDeadField( wordDocument, range,
                        currentTableLevel, c, block );

                if ( skipTo != c )
                {
                    c = skipTo;
                    continue;
                }

                continue;
            }
            if ( text.getBytes()[0] == FIELD_SEPARATOR_MARK )
            {
                // shall not appear without FIELD_BEGIN_MARK
                continue;
            }
            if ( text.getBytes()[0] == FIELD_END_MARK )
            {
                // shall not appear without FIELD_BEGIN_MARK
                continue;
            }

            if ( characterRun.isSpecialCharacter() || characterRun.isObj()
                    || characterRun.isOle2() )
            {
                continue;
            }

            if ( text.endsWith( "\r" )
                    || ( text.charAt( text.length() - 1 ) == BEL_MARK && currentTableLevel != Integer.MIN_VALUE ) )
                text = text.substring( 0, text.length() - 1 );

            {
                // line breaks
                StringBuilder stringBuilder = new StringBuilder();
                for ( char charChar : text.toCharArray() )
                {
                    if ( charChar == 11 )
                    {
                        if ( stringBuilder.length() > 0 )
                        {
                            outputCharacters( block, characterRun,
                                    stringBuilder.toString() );
                            stringBuilder.setLength( 0 );
                        }
                        processLineBreak( block, characterRun );
                    }
                    else if ( charChar == 30 )
                    {
                        // Non-breaking hyphens are stored as ASCII 30
                        stringBuilder.append( UNICODECHAR_NONBREAKING_HYPHEN );
                    }
                    else if ( charChar == 31 )
                    {
                        // Non-required hyphens to zero-width space
                        stringBuilder.append( UNICODECHAR_ZERO_WIDTH_SPACE );
                    }
                    else if ( charChar >= 0x20 || charChar == 0x09
                            || charChar == 0x0A || charChar == 0x0D )
                    {
                        stringBuilder.append( charChar );
                    }
                }
                if ( stringBuilder.length() > 0 )
                {
                    outputCharacters( block, characterRun,
                            stringBuilder.toString() );
                    stringBuilder.setLength( 0 );
                }
            }

            haveAnyText |= text.trim().length() != 0;
        }

        return haveAnyText;
    }

    protected void processDeadField( HWPFDocumentCore wordDocument,
            Element currentBlock, Range range, int currentTableLevel,
            int beginMark, int separatorMark, int endMark )
    {
        StringBuilder debug = new StringBuilder( "Unsupported field type: \n" );
        for ( int i = beginMark; i <= endMark; i++ )
        {
            debug.append( "\t" );
            debug.append( range.getCharacterRun( i ) );
            debug.append( "\n" );
        }
        logger.log( POILogger.WARN, debug );

        Range deadFieldValueSubrage = new Range( range.getCharacterRun(
                separatorMark ).getStartOffset() + 1, range.getCharacterRun(
                endMark ).getStartOffset(), range )
        {
            @Override
            public String toString()
            {
                return "DeadFieldValueSubrange (" + super.toString() + ")";
            }
        };

        // just output field value
        if ( separatorMark + 1 < endMark )
            processCharacters( wordDocument, currentTableLevel,
                    deadFieldValueSubrage, currentBlock );

        return;
    }

    protected Field processDeadField( HWPFDocumentCore wordDocument,
            Range charactersRange, int currentTableLevel, int startOffset,
            Element currentBlock )
    {
        if ( !( wordDocument instanceof HWPFDocument ) )
            return null;

        HWPFDocument hwpfDocument = (HWPFDocument) wordDocument;
        Field field = hwpfDocument.getFields().getFieldByStartOffset(
                FieldsDocumentPart.MAIN, startOffset );
        if ( field == null )
            return null;

        processField( hwpfDocument, charactersRange, currentTableLevel, field,
                currentBlock );

        return field;
    }

    public void processDocument( HWPFDocumentCore wordDocument )
    {
        try
        {
            final SummaryInformation summaryInformation = wordDocument
                    .getSummaryInformation();
            if ( summaryInformation != null )
            {
                processDocumentInformation( summaryInformation );
            }
        }
        catch ( Exception exc )
        {
            logger.log( POILogger.WARN,
                    "Unable to process document summary information: ", exc,
                    exc );
        }

        final Range docRange = wordDocument.getRange();

        if ( docRange.numSections() == 1 )
        {
            processSingleSection( wordDocument, docRange.getSection( 0 ) );
            afterProcess();
            return;
        }

        processDocumentPart( wordDocument, docRange );
        afterProcess();
    }

    protected abstract void processDocumentInformation(
            SummaryInformation summaryInformation );

    protected void processDocumentPart( HWPFDocumentCore wordDocument,
            final Range range )
    {
        for ( int s = 0; s < range.numSections(); s++ )
        {
            processSection( wordDocument, range.getSection( s ), s );
        }
    }

    protected void processDrawnObject( HWPFDocument doc,
            CharacterRun characterRun, Element block )
    {
        if ( getPicturesManager() == null )
            return;

        // TODO: support headers
        OfficeDrawing officeDrawing = doc.getOfficeDrawingsMain()
                .getOfficeDrawingAt( characterRun.getStartOffset() );
        if ( officeDrawing == null )
        {
            logger.log( POILogger.WARN, "Characters #" + characterRun
                    + " references missing drawn object" );
            return;
        }

        byte[] pictureData = officeDrawing.getPictureData();
        if ( pictureData == null )
            // usual shape?
            return;

        final PictureType type = PictureType.findMatchingType( pictureData );
        String path = getPicturesManager().savePicture( pictureData, type,
                "s" + characterRun.getStartOffset() + "." + type );

        processDrawnObject( doc, characterRun, officeDrawing, path, block );
    }

    protected abstract void processDrawnObject( HWPFDocument doc,
            CharacterRun characterRun, OfficeDrawing officeDrawing,
            String path, Element block );

    protected abstract void processEndnoteAutonumbered(
            HWPFDocument wordDocument, int noteIndex, Element block,
            Range endnoteTextRange );

    protected void processField( HWPFDocument wordDocument, Range parentRange,
            int currentTableLevel, Field field, Element currentBlock )
    {
        switch ( field.getType() )
        {
        case 37: // page reference
        {
            final Range firstSubrange = field.firstSubrange( parentRange );
            if ( firstSubrange != null )
            {
                String formula = firstSubrange.text();
                Pattern pagerefPattern = Pattern
                        .compile( "[ \\t\\r\\n]*PAGEREF ([^ ]*)[ \\t\\r\\n]*\\\\h[ \\t\\r\\n]*" );
                Matcher matcher = pagerefPattern.matcher( formula );
                if ( matcher.find() )
                {
                    String pageref = matcher.group( 1 );
                    processPageref( wordDocument, currentBlock,
                            field.secondSubrange( parentRange ),
                            currentTableLevel, pageref );
                    return;
                }
            }
            break;
        }
        case 58: // Embedded Object
        {
            if ( !field.hasSeparator() )
            {
                logger.log( POILogger.WARN, parentRange + " contains " + field
                        + " with 'Embedded Object' but without separator mark" );
                return;
            }

            CharacterRun separator = field
                    .getMarkSeparatorCharacterRun( parentRange );

            if ( separator.isOle2() )
            {
                // the only supported so far
                boolean processed = processOle2( wordDocument, separator,
                        currentBlock );

                // if we didn't output OLE - output field value
                if ( !processed )
                {
                    processCharacters( wordDocument, currentTableLevel,
                            field.secondSubrange( parentRange ), currentBlock );
                }

                return;
            }

            break;
        }
        case 88: // hyperlink
        {
            final Range firstSubrange = field.firstSubrange( parentRange );
            if ( firstSubrange != null )
            {
                String formula = firstSubrange.text();
                Pattern hyperlinkPattern = Pattern
                        .compile( "[ \\t\\r\\n]*HYPERLINK \"(.*)\"[ \\t\\r\\n]*" );
                Matcher matcher = hyperlinkPattern.matcher( formula );
                if ( matcher.find() )
                {
                    String hyperlink = matcher.group( 1 );
                    processHyperlink( wordDocument, currentBlock,
                            field.secondSubrange( parentRange ),
                            currentTableLevel, hyperlink );
                    return;
                }
            }
            break;
        }
        }

        logger.log( POILogger.WARN, parentRange + " contains " + field
                + " with unsupported type or format" );
        processCharacters( wordDocument, currentTableLevel,
                field.secondSubrange( parentRange ), currentBlock );
    }

    protected abstract void processFootnoteAutonumbered(
            HWPFDocument wordDocument, int noteIndex, Element block,
            Range footnoteTextRange );

    protected abstract void processHyperlink( HWPFDocumentCore wordDocument,
            Element currentBlock, Range textRange, int currentTableLevel,
            String hyperlink );

    protected abstract void processImage( Element currentBlock,
            boolean inlined, Picture picture );

    protected abstract void processLineBreak( Element block,
            CharacterRun characterRun );

    protected void processNoteAnchor( HWPFDocument doc,
            CharacterRun characterRun, final Element block )
    {
        {
            Notes footnotes = doc.getFootnotes();
            int noteIndex = footnotes
                    .getNoteIndexByAnchorPosition( characterRun
                            .getStartOffset() );
            if ( noteIndex != -1 )
            {
                Range footnoteRange = doc.getFootnoteRange();
                int rangeStartOffset = footnoteRange.getStartOffset();
                int noteTextStartOffset = footnotes
                        .getNoteTextStartOffset( noteIndex );
                int noteTextEndOffset = footnotes
                        .getNoteTextEndOffset( noteIndex );

                Range noteTextRange = new Range( rangeStartOffset
                        + noteTextStartOffset, rangeStartOffset
                        + noteTextEndOffset, doc );

                processFootnoteAutonumbered( doc, noteIndex, block,
                        noteTextRange );
                return;
            }
        }
        {
            Notes endnotes = doc.getEndnotes();
            int noteIndex = endnotes.getNoteIndexByAnchorPosition( characterRun
                    .getStartOffset() );
            if ( noteIndex != -1 )
            {
                Range endnoteRange = doc.getEndnoteRange();
                int rangeStartOffset = endnoteRange.getStartOffset();
                int noteTextStartOffset = endnotes
                        .getNoteTextStartOffset( noteIndex );
                int noteTextEndOffset = endnotes
                        .getNoteTextEndOffset( noteIndex );

                Range noteTextRange = new Range( rangeStartOffset
                        + noteTextStartOffset, rangeStartOffset
                        + noteTextEndOffset, doc );

                processEndnoteAutonumbered( doc, noteIndex, block,
                        noteTextRange );
                return;
            }
        }
    }

    private boolean processOle2( HWPFDocument doc, CharacterRun characterRun,
            Element block )
    {
        Entry entry = doc.getObjectsPool().getObjectById(
                "_" + characterRun.getPicOffset() );
        if ( entry == null )
        {
            logger.log( POILogger.WARN, "Referenced OLE2 object '",
                    Integer.valueOf( characterRun.getPicOffset() ),
                    "' not found in ObjectPool" );
            return false;
        }

        try
        {
            return processOle2( doc, block, entry );
        }
        catch ( Exception exc )
        {
            logger.log( POILogger.WARN,
                    "Unable to convert internal OLE2 object '",
                    Integer.valueOf( characterRun.getPicOffset() ), "': ", exc,
                    exc );
            return false;
        }
    }

    @SuppressWarnings( "unused" )
    protected boolean processOle2( HWPFDocument wordDocument, Element block,
            Entry entry ) throws Exception
    {
        return false;
    }

    protected abstract void processPageBreak( HWPFDocumentCore wordDocument,
            Element flow );

    protected abstract void processPageref( HWPFDocumentCore wordDocument,
            Element currentBlock, Range textRange, int currentTableLevel,
            String pageref );

    protected abstract void processParagraph( HWPFDocumentCore wordDocument,
            Element parentElement, int currentTableLevel, Paragraph paragraph,
            String bulletText );

    protected void processParagraphes( HWPFDocumentCore wordDocument,
            Element flow, Range range, int currentTableLevel )
    {
        final ListTables listTables = wordDocument.getListTables();
        int currentListInfo = 0;

        final int paragraphs = range.numParagraphs();
        for ( int p = 0; p < paragraphs; p++ )
        {
            Paragraph paragraph = range.getParagraph( p );

            if ( paragraph.isInTable()
                    && paragraph.getTableLevel() != currentTableLevel )
            {
                if ( paragraph.getTableLevel() < currentTableLevel )
                    throw new IllegalStateException(
                            "Trying to process table cell with higher level ("
                                    + paragraph.getTableLevel()
                                    + ") than current table level ("
                                    + currentTableLevel
                                    + ") as inner table part" );

                Table table = range.getTable( paragraph );
                processTable( wordDocument, flow, table );

                p += table.numParagraphs();
                p--;
                continue;
            }

            if ( paragraph.text().equals( "\u000c" ) )
            {
                processPageBreak( wordDocument, flow );
            }

            if ( paragraph.getIlfo() != currentListInfo )
            {
                currentListInfo = paragraph.getIlfo();
            }

            if ( currentListInfo != 0 )
            {
                if ( listTables != null )
                {
                    final ListFormatOverride listFormatOverride = listTables
                            .getOverride( paragraph.getIlfo() );

                    String label = AbstractWordUtils.getBulletText( listTables,
                            paragraph, listFormatOverride.getLsid() );

                    processParagraph( wordDocument, flow, currentTableLevel,
                            paragraph, label );
                }
                else
                {
                    logger.log( POILogger.WARN,
                            "Paragraph #" + paragraph.getStartOffset() + "-"
                                    + paragraph.getEndOffset()
                                    + " has reference to list structure #"
                                    + currentListInfo
                                    + ", but listTables not defined in file" );

                    processParagraph( wordDocument, flow, currentTableLevel,
                            paragraph, AbstractWordUtils.EMPTY );
                }
            }
            else
            {
                processParagraph( wordDocument, flow, currentTableLevel,
                        paragraph, AbstractWordUtils.EMPTY );
            }
        }

    }

    protected abstract void processSection( HWPFDocumentCore wordDocument,
            Section section, int s );

    protected void processSingleSection( HWPFDocumentCore wordDocument,
            Section section )
    {
        processSection( wordDocument, section, 0 );
    }

    protected abstract void processTable( HWPFDocumentCore wordDocument,
            Element flow, Table table );

    public void setFontReplacer( FontReplacer fontReplacer )
    {
        this.fontReplacer = fontReplacer;
    }

    public void setPicturesManager( PicturesManager fileManager )
    {
        this.picturesManager = fileManager;
    }

    protected int tryDeadField( HWPFDocumentCore wordDocument, Range range,
            int currentTableLevel, int beginMark, Element currentBlock )
    {
        int separatorMark = -1;
        int endMark = -1;
        for ( int c = beginMark + 1; c < range.numCharacterRuns(); c++ )
        {
            CharacterRun characterRun = range.getCharacterRun( c );

            String text = characterRun.text();
            if ( text.getBytes().length == 0 )
                continue;

            if ( text.getBytes()[0] == FIELD_BEGIN_MARK )
            {
                // nested?
                Field possibleField = processDeadField( wordDocument, range,
                        currentTableLevel, characterRun.getStartOffset(),
                        currentBlock );
                if ( possibleField != null )
                {
                    c = possibleField.getFieldEndOffset();
                }
                else
                {
                    continue;
                }
            }

            if ( text.getBytes()[0] == FIELD_SEPARATOR_MARK )
            {
                if ( separatorMark != -1 )
                {
                    // double;
                    return beginMark;
                }

                separatorMark = c;
                continue;
            }

            if ( text.getBytes()[0] == FIELD_END_MARK )
            {
                if ( endMark != -1 )
                {
                    // double;
                    return beginMark;
                }

                endMark = c;
                break;
            }

        }

        if ( separatorMark == -1 || endMark == -1 )
            return beginMark;

        processDeadField( wordDocument, currentBlock, range, currentTableLevel,
                beginMark, separatorMark, endMark );

        return endMark;
    }

}
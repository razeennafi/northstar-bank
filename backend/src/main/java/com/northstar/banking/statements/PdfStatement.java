/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: PdfStatement.java
 * Description: Renders a statement one page at a time with PDFBox's disk-backed stream cache.
 * Project version: 0.1.0
 */

package com.northstar.banking.statements;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import com.northstar.banking.ledger.LedgerReporting;
import com.northstar.banking.ledger.LedgerWindow;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Renders a statement one page at a time with PDFBox's disk-backed stream cache. */
final class PdfStatement implements AutoCloseable {
    private static final int LEFT = 46;
    private final PDDocument document = new PDDocument(IOUtils.createTempFileOnlyStreamCache());
    private final PDType1Font heading = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font text = new PDType1Font(Standard14Fonts.FontName.COURIER);
    private final StatementService.StatementSummary summary;
    private final LedgerWindow window;
    private PDPageContentStream page;
    private int y;

    PdfStatement(StatementService.StatementSummary summary, LedgerWindow window) {
        this.summary = summary;
        this.window = window;
    }

    /** Opens the first page so an empty statement still includes its heading and totals. */
    void start() throws IOException {
        newPage();
    }

    /** Writes one ledger row and starts a new page when needed. */
    void addEntry(LedgerReporting.Entry entry) {
        try {
            if (y < 75) {
                newPage();
            }
            line("%-21s %-7s %20s  %-12s  %s".formatted(entry.createdAt().toString().substring(0, 19).replace('T', ' '),
                    entry.direction(), entry.amount(), entry.type(), entry.status()), text, 9);
            line("Ref " + entry.transferId(), text, 8);
            y -= 5;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Finishes the drawing stream before serializing the PDF to the response. */
    void save(OutputStream out) throws IOException {
        closePage();
        document.save(out);
    }

    /** Starts a page with the statement heading and column labels. */
    private void newPage() throws IOException {
        closePage();
        var sheet = new PDPage(PDRectangle.LETTER);
        document.addPage(sheet);
        page = new PDPageContentStream(document, sheet);
        y = 738;
        line("Northstar Bank", heading, 19);
        line("Account statement (CAD)  |  Page " + document.getNumberOfPages(), heading, 12);
        y -= 10;
        line("Account " + summary.accountId(), text, 10);
        line(window.start().toLocalDate() + " through " + window.endExclusive().toLocalDate().minusDays(1)
                + " (UTC)", text, 10);
        line("Opening balance " + summary.openingBalance() + "     Closing balance " + summary.closingBalance(), text, 10);
        line("Credits " + summary.credits() + "     Debits " + summary.debits(), text, 10);
        y -= 14;
        line("Date/time (UTC)       Entry                 CAD  Type          Status", text, 9);
        y -= 7;
    }

    /** Draws one text line at the current page position. */
    private void line(String value, PDType1Font font, int size) throws IOException {
        page.beginText();
        page.setFont(font, size);
        page.newLineAtOffset(LEFT, y);
        page.showText(value);
        page.endText();
        y -= size + 5;
    }

    /** Releases the active page stream before switching pages or saving. */
    private void closePage() throws IOException {
        if (page != null) {
            var active = page;
            page = null;
            active.close();
        }
    }

    /** Releases both the drawing stream and PDF document when export completes. */
    @Override
    public void close() throws IOException {
        try {
            closePage();
        } finally {
            document.close();
        }
    }
}

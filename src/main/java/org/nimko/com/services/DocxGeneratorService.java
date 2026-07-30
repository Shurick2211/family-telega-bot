package org.nimko.com.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocxGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(DocxGeneratorService.class);

  public byte[] generateDocxFromMarkdown(final String text) {
    if (text == null) {
      return new byte[0];
    }

    try (final XWPFDocument document = new XWPFDocument();
         final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final String[] lines = text.split("\n");
      boolean firstParagraph = true;

      for (int i = 0; i < lines.length; i++) {
        final String line = lines[i].trim();
        if (line.isEmpty()) {
          continue;
        }

        if (isTableRow(line) && i + 1 < lines.length && isTableSeparatorRow(lines[i + 1].trim())) {
          final List<String[]> tableRows = new ArrayList<>();
          tableRows.add(splitTableRow(line));
          i += 2; // skip header and separator rows
          while (i < lines.length && isTableRow(lines[i].trim())) {
            tableRows.add(splitTableRow(lines[i].trim()));
            i++;
          }
          i--; // compensate for the loop's increment
          addTable(document, tableRows);
          continue;
        }

        if (line.startsWith("# ")) {
          // Title
          final String titleText = line.substring(2).trim();
          final XWPFParagraph titlePara = document.createParagraph();
          titlePara.setAlignment(ParagraphAlignment.CENTER);
          titlePara.setSpacingBefore(200);
          titlePara.setSpacingAfter(200);
          final XWPFRun titleRun = titlePara.createRun();
          titleRun.setText(titleText);
          titleRun.setBold(true);
          titleRun.setFontSize(20);
          titleRun.setFontFamily("Times New Roman");
          firstParagraph = false;
        } else if (line.startsWith("## ")) {
          // Subtitle / Heading 2
          final String headingText = line.substring(3).trim();
          final XWPFParagraph headingPara = document.createParagraph();
          headingPara.setSpacingBefore(180);
          headingPara.setSpacingAfter(100);
          final XWPFRun headingRun = headingPara.createRun();
          headingRun.setText(headingText);
          headingRun.setBold(true);
          headingRun.setFontSize(16);
          headingRun.setFontFamily("Times New Roman");
        } else if (line.startsWith("### ")) {
          // Heading 3
          final String headingText = line.substring(4).trim();
          final XWPFParagraph headingPara = document.createParagraph();
          headingPara.setSpacingBefore(150);
          headingPara.setSpacingAfter(80);
          final XWPFRun headingRun = headingPara.createRun();
          headingRun.setText(headingText);
          headingRun.setBold(true);
          headingRun.setFontSize(14);
          headingRun.setFontFamily("Times New Roman");
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
          // Bullet list item
          final String bulletText = line.substring(2).trim();
          final XWPFParagraph bulletPara = document.createParagraph();
          bulletPara.setSpacingAfter(60);
          bulletPara.setIndentationLeft(360); // 360 dxa = 18 pt
          final XWPFRun bulletRun = bulletPara.createRun();
          bulletRun.setText("• ");
          addFormattedText(bulletPara, bulletText);
        } else {
          // Regular paragraph
          final XWPFParagraph para = document.createParagraph();
          para.setSpacingAfter(120);
          para.setIndentationFirstLine(400); // 400 dxa first line indentation for ukrainian styling
          para.setAlignment(ParagraphAlignment.BOTH);
          addFormattedText(para, line);
          firstParagraph = false;
        }
      }

      document.write(out);
      return out.toByteArray();
    } catch (final IOException ex) {
      log.error("Failed to generate DOCX file", ex);
      return new byte[0];
    }
  }

  private boolean isTableRow(final String line) {
    return line.startsWith("|") && line.endsWith("|") && line.length() > 1;
  }

  private boolean isTableSeparatorRow(final String line) {
    if (!isTableRow(line)) {
      return false;
    }
    return line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
  }

  private String[] splitTableRow(final String line) {
    final String trimmed = line.substring(1, line.length() - 1);
    final String[] cells = trimmed.split("\\|", -1);
    for (int i = 0; i < cells.length; i++) {
      cells[i] = cells[i].trim();
    }
    return cells;
  }

  private void addTable(final XWPFDocument document, final List<String[]> tableRows) {
    if (tableRows.isEmpty()) {
      return;
    }
    final int columnCount = tableRows.stream().mapToInt(row -> row.length).max().orElse(0);
    if (columnCount == 0) {
      return;
    }

    final XWPFTable table = document.createTable(tableRows.size(), columnCount);
    for (int rowIndex = 0; rowIndex < tableRows.size(); rowIndex++) {
      final String[] cells = tableRows.get(rowIndex);
      final XWPFTableRow tableRow = table.getRow(rowIndex);
      for (int colIndex = 0; colIndex < columnCount; colIndex++) {
        final String cellText = colIndex < cells.length ? cells[colIndex] : "";
        final XWPFTableCell tableCell = tableRow.getCell(colIndex);
        tableCell.removeParagraph(0);
        final XWPFParagraph cellParagraph = tableCell.addParagraph();
        final boolean headerRow = rowIndex == 0;
        addFormattedText(cellParagraph, cellText);
        if (headerRow) {
          cellParagraph.getRuns().forEach(run -> run.setBold(true));
        }
      }
    }

    final XWPFParagraph spacingPara = document.createParagraph();
    spacingPara.setSpacingAfter(120);
  }

  private void addFormattedText(final XWPFParagraph paragraph, final String text) {
    int currentPos = 0;
    while (currentPos < text.length()) {
      final int boldStart = text.indexOf("**", currentPos);
      if (boldStart == -1) {
        final XWPFRun run = paragraph.createRun();
        run.setText(text.substring(currentPos));
        run.setFontFamily("Times New Roman");
        run.setFontSize(12);
        break;
      }

      if (boldStart > currentPos) {
        final XWPFRun run = paragraph.createRun();
        run.setText(text.substring(currentPos, boldStart));
        run.setFontFamily("Times New Roman");
        run.setFontSize(12);
      }

      final int boldEnd = text.indexOf("**", boldStart + 2);
      if (boldEnd == -1) {
        final XWPFRun run = paragraph.createRun();
        run.setText(text.substring(boldStart + 2));
        run.setBold(true);
        run.setFontFamily("Times New Roman");
        run.setFontSize(12);
        break;
      }

      final XWPFRun run = paragraph.createRun();
      run.setText(text.substring(boldStart + 2, boldEnd));
      run.setBold(true);
      run.setFontFamily("Times New Roman");
      run.setFontSize(12);

      currentPos = boldEnd + 2;
    }
  }
}
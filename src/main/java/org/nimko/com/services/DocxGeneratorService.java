package org.nimko.com.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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

      for (final String rawLine : lines) {
        final String line = rawLine.trim();
        if (line.isEmpty()) {
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
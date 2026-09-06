package org.nimko.com.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Service
public class BookTextExtractorService {

  private static final Logger log = LoggerFactory.getLogger(BookTextExtractorService.class);

  public boolean isSupported(final String filename) {
    final String extension = resolveExtension(filename);
    return "txt".equals(extension) || "docx".equals(extension) || "fb2".equals(extension)
        || "pdf".equals(extension);
  }

  public String extractText(final byte[] fileBytes, final String filename) {
    if (fileBytes == null || fileBytes.length == 0) {
      return null;
    }

    try {
      return switch (resolveExtension(filename)) {
        case "txt" -> new String(fileBytes, StandardCharsets.UTF_8);
        case "docx" -> extractFromDocx(fileBytes);
        case "fb2" -> extractFromFb2(fileBytes);
        case "pdf" -> extractFromPdf(fileBytes);
        default -> null;
      };
    } catch (final Exception ex) {
      log.error("Failed to extract text from file {}", filename, ex);
      return null;
    }
  }

  private String resolveExtension(final String filename) {
    if (StringUtils.isBlank(filename) || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
  }

  private String extractFromDocx(final byte[] fileBytes) throws IOException {
    try (final XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
      final StringBuilder builder = new StringBuilder();
      for (final XWPFParagraph paragraph : document.getParagraphs()) {
        final String text = paragraph.getText();
        if (StringUtils.isNotBlank(text)) {
          builder.append(text).append("\n\n");
        }
      }
      return builder.toString().trim();
    }
  }

  private String extractFromPdf(final byte[] fileBytes) throws IOException {
    try (final var document = Loader.loadPDF(fileBytes)) {
      return new PDFTextStripper().getText(document).trim();
    }
  }

  private String extractFromFb2(final byte[] fileBytes)
      throws ParserConfigurationException, IOException, SAXException {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    final DocumentBuilder documentBuilder = factory.newDocumentBuilder();
    final Document document = documentBuilder.parse(new ByteArrayInputStream(fileBytes));

    final NodeList bodies = document.getElementsByTagName("body");
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < bodies.getLength(); i++) {
      final Element body = (Element) bodies.item(i);
      if (body.hasAttribute("name")) {
        continue; // skip footnotes/comments bodies, keep only the main text
      }
      final NodeList paragraphs = body.getElementsByTagName("p");
      for (int j = 0; j < paragraphs.getLength(); j++) {
        final String text = paragraphs.item(j).getTextContent();
        if (StringUtils.isNotBlank(text)) {
          result.append(text.trim()).append("\n\n");
        }
      }
    }
    return result.toString().trim();
  }
}

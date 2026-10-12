package org.nimko.com.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.nimko.com.ai.AiChatService;

public class DocumentTranslationServiceTest {

  private static final Pattern MARKER_PATTERN = Pattern.compile("\\[\\[(\\d+)]]");

  @Test
  public void translatesDocxAndKeepsFormatting() throws Exception {
    final AiChatService aiChatService = mock(AiChatService.class);
    when(aiChatService.askTranslate(anyString(), anyString()))
        .thenAnswer(invocation -> fakeTranslate(invocation.getArgument(1)));

    final DocumentTranslationService service =
        new DocumentTranslationService(aiChatService, new DocxGeneratorService(),
            new BookTextExtractorService());

    final List<Integer> progress = new ArrayList<>();
    final DocumentTranslationService.TranslationResult result =
        service.translate(buildSourceDocx(), "book.docx", "en", progress::add);

    assertNotNull(result);
    assertEquals("docx", result.extension());
    final byte[] translated = result.content();
    assertFalse(progress.isEmpty());
    assertEquals(100, progress.get(progress.size() - 1));

    try (final XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(translated))) {
      final XWPFParagraph title = document.getParagraphs().get(0);
      assertEquals("[en]Назва книги", title.getText());
      assertTrue(title.getRuns().get(0).isBold(), "title must keep its bold formatting");
      assertEquals(ParagraphAlignment.CENTER, title.getAlignment());
      assertEquals("Times New Roman", title.getRuns().get(0).getFontFamily());

      // A paragraph built from several runs is collapsed into the first one.
      assertEquals("[en]Перше речення. Друге речення.", document.getParagraphs().get(1).getText());

      final XWPFTable table = document.getTables().get(0);
      assertEquals("[en]Комірка таблиці",
          table.getRow(0).getCell(0).getParagraphs().get(0).getText());
    }
  }

  @Test
  public void translatesPdfIntoDocx() throws Exception {
    final AiChatService aiChatService = mock(AiChatService.class);
    when(aiChatService.askTranslate(anyString(), anyString()))
        .thenAnswer(invocation -> fakeTranslate(invocation.getArgument(1)));

    final DocumentTranslationService service =
        new DocumentTranslationService(aiChatService, new DocxGeneratorService(),
            new BookTextExtractorService());

    final DocumentTranslationService.TranslationResult result =
        service.translate(buildSourcePdf(), "book.pdf", "sk", progress -> {
        });

    assertNotNull(result);
    assertEquals("docx", result.extension());
    final byte[] translated = result.content();
    try (final XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(translated))) {
      final List<String> texts = document.getParagraphs().stream()
          .map(XWPFParagraph::getText)
          .filter(text -> !text.isBlank())
          .toList();

      // Wrapped lines are glued back into one paragraph, the short line ends it.
      assertEquals(2, texts.size());
      assertEquals("[en]Perse rechennia knyhy, yake rozirvane na dva riadky.", texts.get(0));
      assertEquals("[en]Druhyi abzats.", texts.get(1));
    }
  }

  @Test
  public void translatesPptxAndKeepsItAPresentation() throws Exception {
    final AiChatService aiChatService = mock(AiChatService.class);
    when(aiChatService.askTranslate(anyString(), anyString()))
        .thenAnswer(invocation -> fakeTranslate(invocation.getArgument(1)));

    final DocumentTranslationService service =
        new DocumentTranslationService(aiChatService, new DocxGeneratorService(),
            new BookTextExtractorService());

    final DocumentTranslationService.TranslationResult result =
        service.translate(buildSourcePptx(), "slides.pptx", "sk", progress -> {
        });

    assertNotNull(result);
    assertEquals("pptx", result.extension());

    try (final XMLSlideShow slideShow =
        new XMLSlideShow(new ByteArrayInputStream(result.content()))) {
      final XSLFSlide slide = slideShow.getSlides().get(0);

      final XSLFTextShape textBox = (XSLFTextShape) slide.getShapes().get(0);
      assertEquals("[en]Заголовок слайда", textBox.getTextParagraphs().get(0).getText());
      assertEquals("[en]Перший пункт", textBox.getTextParagraphs().get(1).getText());
      assertTrue(textBox.getTextParagraphs().get(0).getTextRuns().get(0).isBold(),
          "the title must keep its bold formatting");

      final XSLFTable table = (XSLFTable) slide.getShapes().get(1);
      assertEquals("[en]Комірка слайда",
          table.getRows().get(0).getCells().get(0).getTextParagraphs().get(0).getText());
    }
  }

  @Test
  public void translatesLegacyPptAndKeepsItAPresentation() throws Exception {
    final AiChatService aiChatService = mock(AiChatService.class);
    when(aiChatService.askTranslate(anyString(), anyString()))
        .thenAnswer(invocation -> fakeTranslate(invocation.getArgument(1)));

    final DocumentTranslationService service =
        new DocumentTranslationService(aiChatService, new DocxGeneratorService(),
            new BookTextExtractorService());

    final DocumentTranslationService.TranslationResult result =
        service.translate(buildSourcePpt(), "slides.ppt", "en", progress -> {
        });

    assertNotNull(result);
    assertEquals("ppt", result.extension());

    try (final HSLFSlideShow slideShow =
        new HSLFSlideShow(new ByteArrayInputStream(result.content()))) {
      final HSLFTextBox textBox = (HSLFTextBox) slideShow.getSlides().get(0).getShapes().get(0);
      assertEquals("[en]Заголовок слайда", textBox.getText().strip());
    }
  }

  @Test
  public void resolvesLanguageName() {
    final DocumentTranslationService service =
        new DocumentTranslationService(mock(AiChatService.class), new DocxGeneratorService(),
            new BookTextExtractorService());

    assertEquals("English", service.resolveLanguageName("en"));
    assertEquals("German", service.resolveLanguageName("de"));
    assertEquals("Slovak", service.resolveLanguageName("sk"));
    assertEquals("Slovak", service.resolveLanguageName("SK"));
    assertEquals("Chinese", service.resolveLanguageName("zh"));
    assertEquals("Hindi", service.resolveLanguageName("hi"));
    assertEquals("Persian", service.resolveLanguageName("fa"));
    assertEquals("Swahili", service.resolveLanguageName("sw"));
    // Codes people often type by mistake instead of the ISO ones.
    assertEquals("Ukrainian", service.resolveLanguageName("ua"));
    assertEquals("Czech", service.resolveLanguageName("cz"));
    // Everything else: a locale lookup, then the typed word as is.
    assertEquals("Hungarian", service.resolveLanguageName("hu"));
    assertEquals("japanese", service.resolveLanguageName("japanese"));
    assertEquals("English", service.resolveLanguageName(null));
  }

  @Test
  public void rejectsUnsupportedFormats() {
    final DocumentTranslationService service =
        new DocumentTranslationService(mock(AiChatService.class), new DocxGeneratorService(),
            new BookTextExtractorService());

    assertTrue(service.isSupported("book.docx"));
    assertTrue(service.isSupported("BOOK.DOC"));
    assertTrue(service.isSupported("book.pdf"));
    assertTrue(service.isSupported("slides.pptx"));
    assertTrue(service.isSupported("slides.PPT"));
    assertFalse(service.isSupported("book.fb2"));
    assertFalse(service.isSupported("book"));
  }

  /** Answers like the AI provider would: the same markers, with a prefixed "translation". */
  private String fakeTranslate(final String payload) {
    final StringBuilder response = new StringBuilder();
    final Matcher matcher = MARKER_PATTERN.matcher(payload);

    int marker = -1;
    int textStart = -1;
    while (matcher.find()) {
      if (marker > 0) {
        appendSegment(response, marker, payload.substring(textStart, matcher.start()));
      }
      marker = Integer.parseInt(matcher.group(1));
      textStart = matcher.end();
    }
    if (marker > 0) {
      appendSegment(response, marker, payload.substring(textStart));
    }
    return response.toString();
  }

  private void appendSegment(final StringBuilder response, final int marker, final String text) {
    response.append("[[").append(marker).append("]]\n[en]").append(text.strip()).append("\n\n");
  }

  private byte[] buildSourcePptx() throws Exception {
    try (final XMLSlideShow slideShow = new XMLSlideShow();
        final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final XSLFSlide slide = slideShow.createSlide();

      final XSLFTextBox textBox = slide.createTextBox();
      textBox.clearText();
      textBox.addNewTextParagraph().addNewTextRun().setText("Заголовок слайда");
      textBox.getTextParagraphs().get(0).getTextRuns().get(0).setBold(true);
      textBox.addNewTextParagraph().addNewTextRun().setText("Перший пункт");

      final XSLFTable table = slide.createTable(1, 1);
      table.getRows().get(0).getCells().get(0).setText("Комірка слайда");

      slideShow.write(out);
      return out.toByteArray();
    }
  }

  private byte[] buildSourcePpt() throws Exception {
    try (final HSLFSlideShow slideShow = new HSLFSlideShow();
        final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final HSLFTextBox textBox = slideShow.createSlide().createTextBox();
      textBox.setText("Заголовок слайда");

      slideShow.write(out);
      return out.toByteArray();
    }
  }

  private byte[] buildSourcePdf() throws Exception {
    try (final PDDocument document = new PDDocument();
        final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final PDPage page = new PDPage();
      document.addPage(page);
      try (final PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.beginText();
        content.newLineAtOffset(50, 700);
        content.showText("Perse rechennia knyhy, yake rozirvane");
        content.newLineAtOffset(0, -16);
        content.showText("na dva riadky.");
        content.newLineAtOffset(0, -16);
        content.showText("Druhyi abzats.");
        content.endText();
      }

      document.save(out);
      return out.toByteArray();
    }
  }

  private byte[] buildSourceDocx() throws Exception {
    try (final XWPFDocument document = new XWPFDocument();
        final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final XWPFParagraph title = document.createParagraph();
      title.setAlignment(ParagraphAlignment.CENTER);
      final XWPFRun titleRun = title.createRun();
      titleRun.setText("Назва книги");
      titleRun.setBold(true);
      titleRun.setFontFamily("Times New Roman");

      final XWPFParagraph body = document.createParagraph();
      body.createRun().setText("Перше речення. ");
      body.createRun().setText("Друге речення.");

      document.createParagraph(); // empty paragraph must survive untouched

      final XWPFTable table = document.createTable(1, 1);
      table.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("Комірка таблиці");

      document.write(out);
      return out.toByteArray();
    }
  }
}

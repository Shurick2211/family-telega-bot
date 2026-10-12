package org.nimko.com.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.util.BotUtils;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Translates .doc/.docx books into another language with the AI provider and returns a .docx file.
 *
 * <p>For .docx the original document is reused as a template: only the text of the paragraphs is
 * replaced, so styles, headings, tables, headers and footers of the source book are preserved. The
 * legacy .doc format has no such template support, therefore its text is re-rendered into a new
 * .docx via {@link DocxGeneratorService}.
 */
@Service
public class DocumentTranslationService {

  private static final Logger log = LoggerFactory.getLogger(DocumentTranslationService.class);

  private static final String TARGET_LANGUAGE_PLACEHOLDER = "{{TARGET_LANGUAGE}}";
  private static final String DEFAULT_LANGUAGE_CODE = "en";
  /** Maximum amount of characters sent to the AI provider within a single request. */
  private static final int MAX_CHUNK_CHARS = 3000;
  /** Maximum amount of paragraphs sent to the AI provider within a single request. */
  private static final int MAX_SEGMENTS_PER_CHUNK = 40;
  private static final int PARALLEL_REQUESTS = 3;
  private static final Pattern MARKER_PATTERN = Pattern.compile("\\[\\[(\\d+)]]");
  /**
   * The 30 most widely spoken languages by their ISO 639-1 codes plus the codes people often type
   * by mistake ({@code ua}, {@code cz}, {@code jp}, {@code cn}, {@code gr}, {@code se}). Anything
   * else is resolved through {@link Locale} in {@link #resolveLanguageName(String)}.
   */
  private static final Map<String, String> LANGUAGE_NAMES = Map.ofEntries(
      Map.entry("en", "English"),
      Map.entry("zh", "Chinese"),
      Map.entry("hi", "Hindi"),
      Map.entry("es", "Spanish"),
      Map.entry("ar", "Arabic"),
      Map.entry("fr", "French"),
      Map.entry("bn", "Bengali"),
      Map.entry("pt", "Portuguese"),
      Map.entry("ru", "Russian"),
      Map.entry("ur", "Urdu"),
      Map.entry("id", "Indonesian"),
      Map.entry("de", "German"),
      Map.entry("ja", "Japanese"),
      Map.entry("sw", "Swahili"),
      Map.entry("mr", "Marathi"),
      Map.entry("te", "Telugu"),
      Map.entry("tr", "Turkish"),
      Map.entry("ta", "Tamil"),
      Map.entry("vi", "Vietnamese"),
      Map.entry("ko", "Korean"),
      Map.entry("it", "Italian"),
      Map.entry("th", "Thai"),
      Map.entry("fa", "Persian"),
      Map.entry("pl", "Polish"),
      Map.entry("uk", "Ukrainian"),
      Map.entry("nl", "Dutch"),
      Map.entry("ro", "Romanian"),
      Map.entry("el", "Greek"),
      Map.entry("cs", "Czech"),
      Map.entry("sk", "Slovak"),
      Map.entry("sv", "Swedish"),
      Map.entry("ua", "Ukrainian"),
      Map.entry("cz", "Czech"),
      Map.entry("jp", "Japanese"),
      Map.entry("cn", "Chinese"),
      Map.entry("gr", "Greek"),
      Map.entry("se", "Swedish"));

  private final AiChatService aiChatService;
  private final DocxGeneratorService docxGeneratorService;

  public DocumentTranslationService(final AiChatService aiChatService,
      final DocxGeneratorService docxGeneratorService) {
    this.aiChatService = aiChatService;
    this.docxGeneratorService = docxGeneratorService;
  }

  public boolean isSupported(final String filename) {
    final String extension = resolveExtension(filename);
    return "doc".equals(extension) || "docx".equals(extension);
  }

  /**
   * Translates the given book and returns the result as a .docx file.
   *
   * @param fileBytes        source .doc/.docx file
   * @param filename         original file name, used to detect the format
   * @param languageCode     target language code or name typed after the dash in the command
   * @param progressCallback receives the translation progress in percent, may be {@code null}
   * @return bytes of the translated .docx file or {@code null} when nothing could be translated
   */
  public byte[] translateToDocx(final byte[] fileBytes, final String filename,
      final String languageCode, final Consumer<Integer> progressCallback) {
    if (fileBytes == null || fileBytes.length == 0) {
      return null;
    }

    final String targetLanguage = resolveLanguageName(languageCode);
    log.info("Translating document {} into {}", filename, targetLanguage);

    try {
      return switch (resolveExtension(filename)) {
        case "docx" -> translateDocx(fileBytes, targetLanguage, progressCallback);
        case "doc" -> translateLegacyDoc(fileBytes, targetLanguage, progressCallback);
        default -> null;
      };
    } catch (final Exception ex) {
      log.error("Failed to translate document {}", filename, ex);
      return null;
    }
  }

  /**
   * Resolves a human readable language name from the command suffix: {@code en} becomes
   * {@code English}, {@code /translate-japanese} keeps the already readable {@code japanese}.
   */
  public String resolveLanguageName(final String languageCode) {
    final String token = StringUtils.isNotBlank(languageCode)
        ? languageCode.trim().replace('_', '-')
        : DEFAULT_LANGUAGE_CODE;

    final String knownLanguage = LANGUAGE_NAMES.get(token.toLowerCase(Locale.ROOT));
    if (knownLanguage != null) {
      return knownLanguage;
    }

    final String displayLanguage = Locale.forLanguageTag(token).getDisplayLanguage(Locale.ENGLISH);
    if (StringUtils.isNotBlank(displayLanguage) && !displayLanguage.equalsIgnoreCase(token)) {
      return displayLanguage;
    }
    return token;
  }

  private byte[] translateDocx(final byte[] fileBytes, final String targetLanguage,
      final Consumer<Integer> progressCallback) throws IOException {
    try (final XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes));
        final ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      final List<XWPFParagraph> paragraphs = collectParagraphs(document);
      final List<String> paragraphTexts = paragraphs.stream().map(XWPFParagraph::getText).toList();

      final Map<Integer, String> translations =
          translateParagraphs(paragraphTexts, targetLanguage, progressCallback);
      if (translations.isEmpty()) {
        return null;
      }

      translations.forEach((index, text) -> applyTranslation(paragraphs.get(index), text));

      document.write(out);
      return out.toByteArray();
    }
  }

  private byte[] translateLegacyDoc(final byte[] fileBytes, final String targetLanguage,
      final Consumer<Integer> progressCallback) throws IOException {
    final List<String> paragraphTexts;
    try (final HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(fileBytes));
        final WordExtractor extractor = new WordExtractor(document)) {
      paragraphTexts = Arrays.stream(extractor.getParagraphText())
          .map(text -> text == null ? "" : text.replace("\r", "").trim())
          .toList();
    }

    final Map<Integer, String> translations =
        translateParagraphs(paragraphTexts, targetLanguage, progressCallback);
    if (translations.isEmpty()) {
      return null;
    }

    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < paragraphTexts.size(); i++) {
      final String text = translations.getOrDefault(i, paragraphTexts.get(i));
      if (StringUtils.isBlank(text)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append("\n\n");
      }
      builder.append(text);
    }

    // The legacy format cannot be reused as a template, so the text is rendered into a fresh .docx.
    return docxGeneratorService.generateDocxFromMarkdown(builder.toString());
  }

  /**
   * Splits the paragraphs into segments, translates them in parallel and glues the parts of every
   * paragraph back together.
   *
   * @return translated text by the index of the source paragraph
   */
  private Map<Integer, String> translateParagraphs(final List<String> paragraphTexts,
      final String targetLanguage, final Consumer<Integer> progressCallback) {
    final List<Segment> segments = buildSegments(paragraphTexts);
    if (segments.isEmpty()) {
      log.warn("Document has no translatable text");
      return Map.of();
    }

    final String systemPrompt =
        BotUtils.translatePrompt().replace(TARGET_LANGUAGE_PLACEHOLDER, targetLanguage);
    final Map<Integer, String> translatedSegments =
        translateSegments(segments, systemPrompt, progressCallback);

    final Map<Integer, StringBuilder> byParagraph = new LinkedHashMap<>();
    for (int i = 0; i < segments.size(); i++) {
      final Segment segment = segments.get(i);
      final String translated = translatedSegments.getOrDefault(i, segment.text());
      final StringBuilder builder =
          byParagraph.computeIfAbsent(segment.paragraphIndex(), key -> new StringBuilder());
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(translated);
    }

    final Map<Integer, String> result = new LinkedHashMap<>();
    byParagraph.forEach((index, builder) -> result.put(index, builder.toString()));
    return result;
  }

  private Map<Integer, String> translateSegments(final List<Segment> segments,
      final String systemPrompt, final Consumer<Integer> progressCallback) {
    final List<List<Integer>> chunks = buildChunks(segments);
    log.info("Translating {} segments in {} chunks", segments.size(), chunks.size());

    final ExecutorService executor =
        Executors.newFixedThreadPool(Math.min(PARALLEL_REQUESTS, chunks.size()));
    final AtomicInteger completedChunks = new AtomicInteger();
    final Map<Integer, String> translations = new ConcurrentHashMap<>();

    try {
      final List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int i = 0; i < chunks.size(); i++) {
        final int chunkIndex = i;
        final List<Integer> chunk = chunks.get(i);
        futures.add(CompletableFuture.runAsync(() -> {
          try {
            translations.putAll(translateChunk(chunk, segments, systemPrompt));
          } catch (final RuntimeException ex) {
            log.error("Failed to translate chunk {}/{}", chunkIndex + 1, chunks.size(), ex);
          }
          if (progressCallback != null) {
            final int progress =
                (int) (((double) completedChunks.incrementAndGet() / chunks.size()) * 100);
            progressCallback.accept(progress);
          }
        }, executor));
      }
      futures.forEach(CompletableFuture::join);
    } finally {
      executor.shutdown();
    }

    log.info("Translated {}/{} segments", translations.size(), segments.size());
    return translations;
  }

  private Map<Integer, String> translateChunk(final List<Integer> chunk,
      final List<Segment> segments, final String systemPrompt) {
    Map<Integer, String> translated = requestTranslation(chunk, segments, systemPrompt);

    if (translated.size() < chunk.size()) {
      log.warn("Chunk translated partially ({}/{}), retrying it once", translated.size(),
          chunk.size());
      final Map<Integer, String> retried = requestTranslation(chunk, segments, systemPrompt);
      if (retried.size() > translated.size()) {
        translated = retried;
      }
    }

    if (translated.size() < chunk.size()) {
      // Translate the segments the provider has skipped one by one as the last resort.
      for (final Integer segmentId : chunk) {
        if (translated.containsKey(segmentId)) {
          continue;
        }
        translated.putAll(requestTranslation(List.of(segmentId), segments, systemPrompt));
      }
    }
    return translated;
  }

  private Map<Integer, String> requestTranslation(final List<Integer> chunk,
      final List<Segment> segments, final String systemPrompt) {
    final StringBuilder payload = new StringBuilder();
    for (final Integer segmentId : chunk) {
      payload.append("[[").append(segmentId + 1).append("]]\n")
          .append(segments.get(segmentId).text().trim()).append("\n\n");
    }

    final String response = aiChatService.askTranslate(systemPrompt, payload.toString().trim());
    if (isFailedResponse(response)) {
      log.warn("AI provider failed to translate a chunk: {}", response);
      return new LinkedHashMap<>();
    }

    final Map<Integer, String> parsed = parseResponse(response, chunk);
    if (parsed.isEmpty() && chunk.size() == 1) {
      // A single segment is often answered without the marker — the whole answer is the translation.
      parsed.put(chunk.get(0), response.trim());
    }
    return parsed;
  }

  private Map<Integer, String> parseResponse(final String response, final List<Integer> chunk) {
    final Map<Integer, String> translations = new LinkedHashMap<>();
    final Matcher matcher = MARKER_PATTERN.matcher(response);

    int currentSegmentId = -1;
    int textStart = -1;
    while (matcher.find()) {
      if (currentSegmentId >= 0) {
        addTranslation(translations, chunk, currentSegmentId,
            response.substring(textStart, matcher.start()));
      }
      currentSegmentId = Integer.parseInt(matcher.group(1)) - 1;
      textStart = matcher.end();
    }
    if (currentSegmentId >= 0) {
      addTranslation(translations, chunk, currentSegmentId, response.substring(textStart));
    }
    return translations;
  }

  private void addTranslation(final Map<Integer, String> translations, final List<Integer> chunk,
      final int segmentId, final String text) {
    if (!chunk.contains(segmentId)) {
      log.warn("AI provider returned an unexpected segment marker {}", segmentId + 1);
      return;
    }
    final String trimmed = text.strip();
    if (StringUtils.isNotBlank(trimmed)) {
      translations.put(segmentId, trimmed);
    }
  }

  private boolean isFailedResponse(final String response) {
    return StringUtils.isBlank(response) || response.startsWith("AI provider");
  }

  private List<List<Integer>> buildChunks(final List<Segment> segments) {
    final List<List<Integer>> chunks = new ArrayList<>();
    List<Integer> currentChunk = new ArrayList<>();
    int currentLength = 0;

    for (int i = 0; i < segments.size(); i++) {
      final int segmentLength = segments.get(i).text().length();
      if (!currentChunk.isEmpty() && (currentLength + segmentLength > MAX_CHUNK_CHARS
          || currentChunk.size() >= MAX_SEGMENTS_PER_CHUNK)) {
        chunks.add(currentChunk);
        currentChunk = new ArrayList<>();
        currentLength = 0;
      }
      currentChunk.add(i);
      currentLength += segmentLength;
    }
    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk);
    }
    return chunks;
  }

  private List<Segment> buildSegments(final List<String> paragraphTexts) {
    final List<Segment> segments = new ArrayList<>();
    for (int i = 0; i < paragraphTexts.size(); i++) {
      final String text = paragraphTexts.get(i);
      if (StringUtils.isBlank(text)) {
        continue;
      }
      if (text.length() <= MAX_CHUNK_CHARS) {
        segments.add(new Segment(i, text));
        continue;
      }
      for (final String part : splitBySentences(text)) {
        segments.add(new Segment(i, part));
      }
    }
    return segments;
  }

  private List<String> splitBySentences(final String text) {
    final List<String> parts = new ArrayList<>();
    final StringBuilder currentPart = new StringBuilder();

    for (final String sentence : text.split("(?<=[.!?…])\\s+")) {
      final String trimmedSentence = sentence.trim();
      if (trimmedSentence.isEmpty()) {
        continue;
      }
      if (currentPart.length() > 0 && currentPart.length() + trimmedSentence.length()
          > MAX_CHUNK_CHARS) {
        parts.add(currentPart.toString());
        currentPart.setLength(0);
      }
      if (currentPart.length() > 0) {
        currentPart.append(' ');
      }
      currentPart.append(trimmedSentence);
    }
    if (currentPart.length() > 0) {
      parts.add(currentPart.toString());
    }
    return parts;
  }

  private List<XWPFParagraph> collectParagraphs(final XWPFDocument document) {
    final List<XWPFParagraph> paragraphs = new ArrayList<>();
    collectFromBody(document, paragraphs);
    for (final XWPFHeader header : document.getHeaderList()) {
      collectFromBody(header, paragraphs);
    }
    for (final XWPFFooter footer : document.getFooterList()) {
      collectFromBody(footer, paragraphs);
    }
    return paragraphs;
  }

  private void collectFromBody(final IBody body, final List<XWPFParagraph> paragraphs) {
    for (final IBodyElement element : body.getBodyElements()) {
      if (element instanceof final XWPFParagraph paragraph) {
        paragraphs.add(paragraph);
      } else if (element instanceof final XWPFTable table) {
        collectFromTable(table, paragraphs);
      }
    }
  }

  private void collectFromTable(final XWPFTable table, final List<XWPFParagraph> paragraphs) {
    for (final XWPFTableRow row : table.getRows()) {
      for (final XWPFTableCell cell : row.getTableCells()) {
        collectFromBody(cell, paragraphs);
      }
    }
  }

  /**
   * Writes the translated text into the first meaningful run of the paragraph and clears the rest,
   * so the paragraph keeps its original style, font and alignment.
   */
  private void applyTranslation(final XWPFParagraph paragraph, final String text) {
    final List<XWPFRun> runs = paragraph.getRuns();
    if (runs == null || runs.isEmpty()) {
      writeText(paragraph.createRun(), text);
      return;
    }

    int targetIndex = 0;
    for (int i = 0; i < runs.size(); i++) {
      if (StringUtils.isNotBlank(runs.get(i).text())) {
        targetIndex = i;
        break;
      }
    }

    for (int i = 0; i < runs.size(); i++) {
      clearRunText(runs.get(i));
    }
    writeText(runs.get(targetIndex), text);
  }

  private void clearRunText(final XWPFRun run) {
    final CTR ctr = run.getCTR();
    for (int i = ctr.sizeOfTArray() - 1; i >= 0; i--) {
      ctr.removeT(i);
    }
  }

  private void writeText(final XWPFRun run, final String text) {
    final String[] lines = text.split("\n", -1);
    run.setText(lines[0], 0);
    for (int i = 1; i < lines.length; i++) {
      run.addBreak();
      run.setText(lines[i]);
    }
  }

  private String resolveExtension(final String filename) {
    if (StringUtils.isBlank(filename) || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  private record Segment(int paragraphIndex, String text) {

  }
}

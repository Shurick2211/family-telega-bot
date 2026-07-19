package org.nimko.com.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DocxGeneratorServiceTest {

  @Test
  public void testGenerateDocxFromMarkdown() {
    final DocxGeneratorService service = new DocxGeneratorService();
    final String markdownText = "# Title\n"
        + "## Subtitle\n"
        + "This is a regular paragraph with **bold text** in it.\n"
        + "- Bullet point 1\n"
        + "- Bullet point 2 with **bold** word\n"
        + "Another regular paragraph.";

    final byte[] docxBytes = service.generateDocxFromMarkdown(markdownText);

    assertNotNull(docxBytes);
    assertTrue(docxBytes.length > 0);
  }
}
package org.nimko.com.util;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadResourceUtils {

  private static final Logger log = LoggerFactory.getLogger(ReadResourceUtils.class);

  public static String readResourceFile(final String resourcePath) {
    final String normalizedPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    try (final var inputStream = ReadResourceUtils.class.getClassLoader().getResourceAsStream(normalizedPath)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("Resource not found: " + normalizedPath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final Exception ex) {
      log.error("Failed to read resource file: {}", resourcePath, ex);
      throw new RuntimeException("Failed to read resource: " + resourcePath, ex);
    }
  }
}

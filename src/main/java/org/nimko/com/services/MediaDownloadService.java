package org.nimko.com.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that downloads media from an external downloader service (http://localhost:9001/download)
 * in a separate thread and notifies a callback (FileSender) to deliver the result into Telegram.
 */
public class MediaDownloadService {
  private static final Logger log = LoggerFactory.getLogger(MediaDownloadService.class);

  public interface FileSender {
    void sendText(Long chatId, String text);
    void sendFile(Long chatId, byte[] bytes, String filename, String contentType);
  }

  private final FileSender sender;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private final String downloaderEndpoint;

  public MediaDownloadService(final FileSender sender, final String downloaderEndpoint) {
    this.sender = sender;
    this.downloaderEndpoint = downloaderEndpoint;
  }

  public void submitDownload(final Long chatId, final String url, final Locale locale) throws IllegalArgumentException{
    executor.submit(() -> {
      try {
        TranslationContext.setLocale(locale);

        final String payload = "{\"url\":\"" + url.replace("\"", "\\\"") + "\"}";
        final HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(downloaderEndpoint))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(payload))
            .build();

        final HttpResponse<byte[]> resp = http.send(req, BodyHandlers.ofByteArray());
        final int status = resp.statusCode();
        if (status >= 200 && status < 300 && resp.body() != null && resp.body().length > 0) {
          final String contentType = resp.headers().firstValue("Content-Type").orElse("application/octet-stream");
          final String filename = extractFilenameFromUrl(url);
          sender.sendFile(chatId, resp.body(), filename, contentType);
        } else {
          log.warn("Downloader returned status {} for url {}", status, url);
          sender.sendText(chatId, "Failed to download media. Service returned status: " + status);
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Download interrupted for url {}", url, e);
        throw new IllegalArgumentException(e);
      } catch (final IOException e) {
        log.error("I/O error while downloading url {}", url, e);
        throw new IllegalArgumentException(e);
      } catch (final Exception e) {
        log.error("Unexpected error while downloading url {}", url, e);
        throw new IllegalArgumentException(e);
      } finally {
        TranslationContext.clear();
      }
    });
  }

  private static String extractFilenameFromUrl(final String url) {
    try {
      final URI u = URI.create(url);
      final String path = u.getPath();
      if (path == null || path.isBlank()) {
        return "file.mp4";
      }
      final String[] parts = path.split("/");
      final String last = parts[parts.length - 1];
      if (last == null || last.isBlank()) {
        return "file.mp4";
      }
      if (last.contains(".")) {
        return last;
      }
      return last + ".mp4";
    } catch (final Exception e) {
      return "file.mp4";
    }
  }

  public void shutdown() {
    executor.shutdownNow();
  }
}

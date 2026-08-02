package org.nimko.com.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nimko.com.services.dto.BatteryStatusDto;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TermuxService {

  /** Shared storage path where the Android camera stores photos. */
  private static final String DCIM_DIR = "/storage/emulated/0/DCIM/Camera";
  /** Symlink created by {@code termux-setup-storage}; points to the same place as {@link #DCIM_DIR}. */
  private static final String TERMUX_DCIM_DIR =
      System.getenv().getOrDefault("HOME", "/data/data/com.termux/files/home")
          + "/storage/dcim/Camera";
  private static final long PROCESS_TIMEOUT_SECONDS = 30;

  private final ObjectMapper objectMapper;

  public BatteryStatusDto getBatteryStatus() {
    try {
      final String json = runCommand("termux-battery-status");
      return objectMapper.readValue(json, BatteryStatusDto.class);
    } catch (final Exception e) {
      log.error("Failed to get battery status via Termux API", e);
      throw new RuntimeException("Failed to get battery status", e);
    }
  }

  public void speak(final String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Text for termux-tts-speak must not be empty");
    }
    try {
      runCommand("termux-tts-speak", text);
    } catch (final Exception e) {
      log.error("Failed to speak text via Termux API", e);
      throw new RuntimeException("Failed to speak text", e);
    }
  }

  public byte[] takePhoto(final boolean useFrontCamera) {
    final File dcimDir = resolvePhotoDir();

    final File photoFile = new File(dcimDir, "photo_" + Instant.now().toEpochMilli() + ".jpg");
    try {
      runCommand("termux-camera-photo", "-c", useFrontCamera ? "1" : "0",
          photoFile.getAbsolutePath());
      if (!photoFile.exists()) {
        throw new RuntimeException("Photo file was not created: " + photoFile.getAbsolutePath());
      }
      return Files.readAllBytes(photoFile.toPath());
    } catch (final Exception e) {
      log.error("Failed to take photo via Termux API", e);
      throw new RuntimeException("Failed to take photo", e);
    }
  }

  /**
   * Returns the first writable photo directory: shared DCIM/Camera, the Termux storage symlink
   * (both require {@code termux-setup-storage}), or the app temp dir as a last resort.
   */
  private File resolvePhotoDir() {
    for (final String candidate : new String[] {DCIM_DIR, TERMUX_DCIM_DIR}) {
      final File dir = new File(candidate);
      if ((dir.isDirectory() || dir.mkdirs()) && dir.canWrite()) {
        return dir;
      }
      log.warn("Photo directory is not writable, trying next candidate: {}", candidate);
    }
    final File fallback = new File(System.getProperty("java.io.tmpdir"));
    log.warn("Falling back to temp directory for photos: {}", fallback);
    return fallback;
  }

  public void setFlashlight(final boolean enabled) {
    try {
      runCommand("termux-torch", enabled ? "on" : "off");
    } catch (final Exception e) {
      log.error("Failed to toggle flashlight via Termux API", e);
      throw new RuntimeException("Failed to toggle flashlight", e);
    }
  }

  public void blinkFlashlight(final int times, final long intervalMillis) {
    try {
      for (int i = 0; i < times; i++) {
        setFlashlight(true);
        Thread.sleep(intervalMillis);
        setFlashlight(false);
        if (i < times - 1) {
          Thread.sleep(intervalMillis);
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Flashlight blinking was interrupted", e);
    }
  }

  private String runCommand(final String... command) throws IOException, InterruptedException {
    final ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    final Process process = pb.start();

    final String output;
    try (final var inputStream = process.getInputStream()) {
      output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    final boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("Termux command timed out: " + String.join(" ", command));
    }

    final int exitCode = process.exitValue();
    if (exitCode != 0) {
      log.error("Termux command '{}' failed with exit code {}: {}", String.join(" ", command), exitCode, output);
      throw new RuntimeException("Termux command exited with error code: " + exitCode);
    }

    return output;
  }
}

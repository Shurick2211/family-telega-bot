package org.nimko.com.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ws.schild.jave.process.ProcessLocator;

@Component
public class AudioConverter {

  private static final Logger log = LoggerFactory.getLogger(AudioConverter.class);

  @Autowired
  private ProcessLocator ffmpegLocator;

  public byte[] convertOggToMp3(final byte[] oggBytes) {
    if (oggBytes == null || oggBytes.length == 0) {
      throw new IllegalArgumentException("Input bytes are empty or null");
    }

    File tempOggFile = null;
    File tempMp3File = null;

    try {
      tempOggFile = File.createTempFile("tg_voice_input", ".ogg");
      tempMp3File = File.createTempFile("tg_voice_output", ".mp3");

      try (final FileOutputStream fos = new FileOutputStream(tempOggFile)) {
        fos.write(oggBytes);
      }

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-i", tempOggFile.getAbsolutePath(),
          "-acodec", "libmp3lame",
          "-q:a", "0",
          "-ar", "44100",
          "-ac", "2",
          tempMp3File.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempMp3File.toPath());

    } catch (final Exception e) {
      log.error("Error during OGG to MP3 conversion in Termux", e);
      throw new RuntimeException("Failed to convert audio", e);
    } finally {
      cleanUpFile(tempOggFile);
      cleanUpFile(tempMp3File);
    }
  }


  public byte[] extractAudioFromVideo(final byte[] videoBytes) {
    if (videoBytes == null || videoBytes.length == 0) {
      throw new IllegalArgumentException("Video bytes are empty or null");
    }

    File tempVideoFile = null;
    File tempMp3File = null;

    try {
      tempVideoFile = File.createTempFile("tg_video_input", ".mp4");
      tempMp3File = File.createTempFile("tg_extracted_audio", ".mp3");

      try (final FileOutputStream fos = new FileOutputStream(tempVideoFile)) {
        fos.write(videoBytes);
      }

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-i", tempVideoFile.getAbsolutePath(),
          "-vn",
          "-acodec", "libmp3lame",
          "-q:a", "0",
          "-ar", "44100",
          "-ac", "2",
          tempMp3File.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempMp3File.toPath());

    } catch (final Exception e) {
      log.error("Error during audio extraction from video in Termux", e);
      throw new RuntimeException("Failed to extract audio", e);
    } finally {
      cleanUpFile(tempVideoFile);
      cleanUpFile(tempMp3File);
    }
  }

  private void runProcess(final ProcessBuilder pb) throws IOException, InterruptedException {
    pb.redirectErrorStream(true);
    final Process process = pb.start();
    final int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new RuntimeException("FFmpeg exited with error code: " + exitCode);
    }
  }

  private void cleanUpFile(final File file) {
    if (file != null && file.exists()) {
      final boolean deleted = file.delete();
      if (!deleted) {
        log.warn("Temporary file could not be deleted: {}", file.getAbsolutePath());
      }
    }
  }
}
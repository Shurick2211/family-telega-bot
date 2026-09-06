package org.nimko.com.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
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


  public byte[] convertWavToMp3(final byte[] wavBytes) {
    if (wavBytes == null || wavBytes.length == 0) {
      throw new IllegalArgumentException("Input bytes are empty or null");
    }

    File tempWavFile = null;
    File tempMp3File = null;

    try {
      tempWavFile = File.createTempFile("tts_input", ".wav");
      tempMp3File = File.createTempFile("tts_output", ".mp3");

      try (final FileOutputStream fos = new FileOutputStream(tempWavFile)) {
        fos.write(wavBytes);
      }

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-i", tempWavFile.getAbsolutePath(),
          "-acodec", "libmp3lame",
          "-q:a", "0",
          "-ar", "44100",
          "-ac", "2",
          tempMp3File.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempMp3File.toPath());

    } catch (final Exception e) {
      log.error("Error during WAV to MP3 conversion", e);
      throw new RuntimeException("Failed to convert audio", e);
    } finally {
      cleanUpFile(tempWavFile);
      cleanUpFile(tempMp3File);
    }
  }

  public byte[] concatenateWavs(final List<byte[]> wavChunks) {
    if (wavChunks == null || wavChunks.isEmpty()) {
      return null;
    }
    if (wavChunks.size() == 1) {
      return wavChunks.get(0);
    }

    final List<File> tempWavFiles = new java.util.ArrayList<>();
    File listFile = null;
    File tempOutputFile = null;

    try {
      listFile = File.createTempFile("concat_list", ".txt");
      tempOutputFile = File.createTempFile("concat_output", ".wav");

      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < wavChunks.size(); i++) {
        final File tempWav = File.createTempFile("concat_chunk_" + i + "_", ".wav");
        tempWavFiles.add(tempWav);
        try (final FileOutputStream fos = new FileOutputStream(tempWav)) {
          fos.write(wavChunks.get(i));
        }
        sb.append("file '").append(tempWav.getAbsolutePath().replace("'", "'\\''")).append("'\n");
      }

      Files.writeString(listFile.toPath(), sb.toString());

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-f", "concat",
          "-safe", "0",
          "-i", listFile.getAbsolutePath(),
          "-c", "copy",
          tempOutputFile.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempOutputFile.toPath());

    } catch (final Exception e) {
      log.error("Error during WAV concatenation", e);
      throw new RuntimeException("Failed to concatenate audio", e);
    } finally {
      if (listFile != null && listFile.exists()) {
        listFile.delete();
      }
      for (final File tempWav : tempWavFiles) {
        cleanUpFile(tempWav);
      }
      cleanUpFile(tempOutputFile);
    }
  }

  public byte[] concatenatePcmAndConvertToWav(final List<byte[]> pcmChunks, final int sampleRate, final int channels) {
    if (pcmChunks == null || pcmChunks.isEmpty()) {
      return null;
    }
    
    File tempPcmFile = null;
    File tempOutputFile = null;

    try {
      tempPcmFile = File.createTempFile("concat_output", ".pcm");
      tempOutputFile = File.createTempFile("concat_output", ".wav");

      try (final FileOutputStream fos = new FileOutputStream(tempPcmFile)) {
        for (final byte[] chunk : pcmChunks) {
          fos.write(chunk);
        }
      }

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-f", "s16le",
          "-ar", String.valueOf(sampleRate),
          "-ac", String.valueOf(channels),
          "-i", tempPcmFile.getAbsolutePath(),
          tempOutputFile.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempOutputFile.toPath());

    } catch (final Exception e) {
      log.error("Error during PCM concatenation and WAV conversion", e);
      throw new RuntimeException("Failed to concatenate audio", e);
    } finally {
      cleanUpFile(tempPcmFile);
      cleanUpFile(tempOutputFile);
    }
  }

  public byte[] concatenatePcmAndConvertToMp3(final List<byte[]> pcmChunks, final int sampleRate, final int channels) {
    if (pcmChunks == null || pcmChunks.isEmpty()) {
      return null;
    }
    
    File tempPcmFile = null;
    File tempOutputFile = null;

    try {
      tempPcmFile = File.createTempFile("concat_output", ".pcm");
      tempOutputFile = File.createTempFile("concat_output", ".mp3");

      try (final FileOutputStream fos = new FileOutputStream(tempPcmFile)) {
        for (final byte[] chunk : pcmChunks) {
          fos.write(chunk);
        }
      }

      final ProcessBuilder pb = new ProcessBuilder(
          ffmpegLocator.getExecutablePath(),
          "-y",
          "-f", "s16le",
          "-ar", String.valueOf(sampleRate),
          "-ac", String.valueOf(channels),
          "-i", tempPcmFile.getAbsolutePath(),
          "-acodec", "libmp3lame",
          "-q:a", "2",
          tempOutputFile.getAbsolutePath()
      );

      runProcess(pb);

      return Files.readAllBytes(tempOutputFile.toPath());

    } catch (final Exception e) {
      log.error("Error during PCM concatenation and MP3 conversion", e);
      throw new RuntimeException("Failed to concatenate audio to MP3", e);
    } finally {
      cleanUpFile(tempPcmFile);
      cleanUpFile(tempOutputFile);
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
    try (final var inputStream = process.getInputStream()) {
      final byte[] output = inputStream.readAllBytes();
      final int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("FFmpeg process failed with exit code {}: {}", exitCode, new String(output));
        throw new RuntimeException("FFmpeg exited with error code: " + exitCode);
      }
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
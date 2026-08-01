package org.nimko.com.util;

import java.io.File;
import org.springframework.stereotype.Component;
import ws.schild.jave.process.ProcessLocator;

@Component
public class TermuxFFMPEGLocator implements ProcessLocator {
  private static final String TERMUX_FFMPEG = "/data/data/com.termux/files/usr/bin/ffmpeg";

  @Override
  public String getExecutablePath() {
    final File termuxFile = new File(TERMUX_FFMPEG);
    if (termuxFile.exists() && termuxFile.canExecute()) {
      return TERMUX_FFMPEG;
    }
    return "ffmpeg";
  }
}
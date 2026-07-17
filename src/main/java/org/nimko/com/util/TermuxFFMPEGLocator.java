package org.nimko.com.util;

import org.springframework.stereotype.Component;
import ws.schild.jave.process.ProcessLocator;

@Component
public class TermuxFFMPEGLocator implements ProcessLocator {
  @Override
  public String getExecutablePath() {
    // Standard path ffmpeg for Termux by Android
    return "/data/data/com.termux/files/usr/bin/ffmpeg";
  }
}
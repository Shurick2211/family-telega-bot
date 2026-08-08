package org.nimko.com.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

  private static final String PAGE = "static/hello.html";
  private static final String CV_PAGE = "static/cv.html";
  private static final String CV_NATALIA_PAGE = "static/resume_natalia.html";
  private static final String CV_SOPHIA_PAGE = "static/resume_sophia.html";

  @GetMapping(value = "/hello", produces = MediaType.TEXT_HTML_VALUE)
  public String hello() throws IOException {
    return new ClassPathResource(PAGE)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @GetMapping(value = "/nimko_o_cv", produces = MediaType.TEXT_HTML_VALUE)
  public String cv() throws IOException {
    return new ClassPathResource(CV_PAGE)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @GetMapping(value = "/natalia_cv", produces = MediaType.TEXT_HTML_VALUE)
  public String natalia_cv() throws IOException {
    return new ClassPathResource(CV_NATALIA_PAGE)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @GetMapping(value = "/sophiya_cv", produces = MediaType.TEXT_HTML_VALUE)
  public String sophiya_cv() throws IOException {
    return new ClassPathResource(CV_SOPHIA_PAGE)
        .getContentAsString(StandardCharsets.UTF_8);
  }
}

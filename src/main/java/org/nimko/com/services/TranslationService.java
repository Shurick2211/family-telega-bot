package org.nimko.com.services;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;

@Service
public class TranslationService {

  private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
  private final MessageSource messageSource;

  public TranslationService(final MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  public String getTranslate(final String key) {
    final Locale locale = TranslationContext.getLocale();
    try {
      return messageSource.getMessage(key, null, locale);
    } catch (final NoSuchMessageException ex) {
      log.warn("Translation key not found: '{}' for locale: '{}'", key, locale);
      return key;
    }
  }

  public String getTranslate(final String key, final Object... args) {
    final Locale locale = TranslationContext.getLocale();
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (final NoSuchMessageException ex) {
      log.warn("Translation key not found: '{}' with args for locale: '{}'", key, locale);
      return key;
    }
  }
}

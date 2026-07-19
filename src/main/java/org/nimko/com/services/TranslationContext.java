package org.nimko.com.services;

import java.util.Locale;

public final class TranslationContext {

  private static final ThreadLocal<Locale> CURRENT_LOCALE = ThreadLocal.withInitial(() -> Locale.forLanguageTag("uk"));

  private TranslationContext() {
  }

  public static Locale getLocale() {
    return CURRENT_LOCALE.get();
  }

  public static void setLocale(final Locale locale) {
    if (locale != null) {
      CURRENT_LOCALE.set(locale);
    }
  }

  public static void clear() {
    CURRENT_LOCALE.remove();
  }
}

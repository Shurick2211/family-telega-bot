package org.nimko.com.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClientResponseException;

/**
 * Downgrades the Gemini model version when the provider reports a rate limit.
 *
 * <p>A model name is split into prefix, version and suffix: {@code gemini-3.8-flash} is
 * version {@code 3.8} with suffix {@code -flash}. When the quota is exhausted the request
 * is retried on the next older version of the same family, keeping the suffix:
 * {@code gemini-3.8-flash} to {@code gemini-3.7-flash} to {@code gemini-3.6-flash}
 * to {@code gemini-3.5-flash}.
 *
 * <p>Families are listed separately because their version lines do not match: there is no
 * {@code gemini-3.8-flash-lite}, the newest lite build is {@code gemini-3.5-flash-lite}.
 * Mixing the two would produce model names the API answers with HTTP 404.
 *
 * <p>Once a family runs out of older versions the chain continues with the next family from
 * {@link #NEXT_FAMILY}, starting at its newest build: a flash model exhausted down to
 * {@code gemini-3.5-flash} carries on with {@code gemini-3.5-flash-lite} and then
 * {@code gemini-3.1-flash-lite}. Lite answers faster and carries no hidden reasoning tokens,
 * so it is a usable last resort when every flash build is rate limited.
 *
 * <p>Anything outside these families - other providers, preview and TTS builds, aliases
 * such as {@code gemini-flash-latest} - gets a single-element chain, so the behaviour
 * stays exactly as before.
 */
final class GeminiModelFallback {

  /**
   * Known 3.x versions per family, newest first. Only models the API actually serves are
   * listed; inventing a name here would turn a rate limit into a 404.
   */
  private static final Map<String, List<String>> VERSIONS_BY_FAMILY = Map.of(
      "-flash", List.of("3.8", "3.7", "3.6", "3.5"),
      "-flash-lite", List.of("3.5", "3.1"));

  /**
   * Where to continue once a family is exhausted. Lite is the last resort, so it has no
   * successor and the chain ends there.
   */
  private static final Map<String, String> NEXT_FAMILY = Map.of("-flash", "-flash-lite");

  private static final Pattern GEMINI_MODEL =
      Pattern.compile("^(gemini-)(\\d+(?:\\.\\d+)*)(-.*)?$", Pattern.CASE_INSENSITIVE);

  /** Quota markers found in the response body when the status is not 429. */
  private static final List<String> RATE_LIMIT_MARKERS = List.of(
      "too many requests",
      "resource_exhausted",
      "rate limit",
      "ratelimitexceeded",
      "quota exceeded",
      "quota_exceeded");

  private static final int MAX_CAUSE_DEPTH = 5;

  private GeminiModelFallback() {
  }

  /**
   * Builds the model chain for a single request: the requested model first, then older
   * versions of the same family, then every version of the next family. Returns a
   * single-element list when there is nothing safe to fall back to.
   */
  static List<String> chainFor(final String model) {
    if (StringUtils.isBlank(model)) {
      return List.of();
    }

    final Matcher matcher = GEMINI_MODEL.matcher(model.trim());
    if (!matcher.matches()) {
      return List.of(model);
    }

    final String prefix = matcher.group(1);
    final String version = matcher.group(2);
    final String suffix = matcher.group(3) == null ? "" : matcher.group(3);

    String family = suffix.toLowerCase(Locale.ROOT);
    if (!VERSIONS_BY_FAMILY.containsKey(family)) {
      return List.of(model);
    }

    final List<String> chain = new ArrayList<>();
    chain.add(model);

    final Set<String> visited = new HashSet<>();
    boolean sameFamily = true;
    while (family != null && visited.add(family)) {
      for (final String candidate : VERSIONS_BY_FAMILY.get(family)) {
        // Within the requested family only older versions are a downgrade; once we move
        // on to the next family every version of it is fair game, newest first.
        if (!sameFamily || compareVersions(candidate, version) < 0) {
          chain.add(prefix + candidate + family);
        }
      }
      family = NEXT_FAMILY.get(family);
      sameFamily = false;
    }

    return List.copyOf(chain);
  }

  /**
   * Tells an exhausted quota apart from any other failure: either by HTTP 429 or by the
   * response text, because some gateways report quota errors under a different status.
   */
  static boolean isRateLimited(final Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      if (current instanceof RestClientResponseException response
          && response.getStatusCode().value() == 429) {
        return true;
      }
      if (containsMarker(current.getMessage())) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean containsMarker(final String message) {
    if (StringUtils.isBlank(message)) {
      return false;
    }
    final String lower = message.toLowerCase(Locale.ROOT);
    return RATE_LIMIT_MARKERS.stream().anyMatch(lower::contains);
  }

  /** Compares versions component by component, so that 3.10 counts as newer than 3.9. */
  private static int compareVersions(final String left, final String right) {
    final String[] leftParts = left.split("\\.");
    final String[] rightParts = right.split("\\.");
    final int length = Math.max(leftParts.length, rightParts.length);

    for (int i = 0; i < length; i++) {
      final int leftPart = partAt(leftParts, i);
      final int rightPart = partAt(rightParts, i);
      if (leftPart != rightPart) {
        return Integer.compare(leftPart, rightPart);
      }
    }
    return 0;
  }

  private static int partAt(final String[] parts, final int index) {
    if (index >= parts.length) {
      return 0;
    }
    try {
      return Integer.parseInt(parts[index]);
    } catch (final NumberFormatException ex) {
      return 0;
    }
  }
}

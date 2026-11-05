package vn.mta.shop.common;

import java.util.Locale;

public final class EmailNormalizer {
  private EmailNormalizer() {}

  public static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }
}

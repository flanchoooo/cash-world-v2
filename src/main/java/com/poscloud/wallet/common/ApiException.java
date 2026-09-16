package com.poscloud.wallet.common;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
  private final String code;

  public ApiException(String code) {
    super(code);
    this.code = code;
  }

  public ApiException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static void require(boolean condition, String code) {
    if (!condition) throw new ApiException(code);
  }
}

package io.spring.api.exception;

public class InvalidOperationException extends RuntimeException {

  public InvalidOperationException(String message) {
    super(message);
  }
}

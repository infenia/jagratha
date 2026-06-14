/*
 * Copyright 2026 Infenia Private Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infenia.yukta.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infenia.yukta.model.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void testConstructor() {
    assertThat(handler).isNotNull();
  }

  @Test
  void testHandleWebExchangeBindException() {
    final var fieldError = mock(org.springframework.validation.FieldError.class);
    when(fieldError.getField()).thenReturn("username");
    when(fieldError.getDefaultMessage()).thenReturn("must not be blank");

    final var bindException = mock(WebExchangeBindException.class);
    when(bindException.getFieldErrors()).thenReturn(List.of(fieldError));

    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.handleWebExchangeBindException(
        bindException,
        HttpHeaders.EMPTY,
        HttpStatus.BAD_REQUEST,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.status()).isEqualTo(400);
          assertThat(body.errors()).hasSize(1);
          assertThat(body.errors().getFirst().field()).isEqualTo("username");
          assertThat(body.errors().getFirst().message()).isEqualTo("must not be blank");
        })
        .verifyComplete();
  }

  @Test
  void testHandleWebExchangeBindException_multipleErrors() {
    final var fieldError1 = mock(org.springframework.validation.FieldError.class);
    when(fieldError1.getField()).thenReturn("email");
    when(fieldError1.getDefaultMessage()).thenReturn("must be a valid email");

    final var fieldError2 = mock(org.springframework.validation.FieldError.class);
    when(fieldError2.getField()).thenReturn("password");
    when(fieldError2.getDefaultMessage()).thenReturn("must be at least 8 characters");

    final var bindException = mock(WebExchangeBindException.class);
    when(bindException.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/register");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.handleWebExchangeBindException(
        bindException,
        HttpHeaders.EMPTY,
        HttpStatus.BAD_REQUEST,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.errors()).hasSize(2);
        })
        .verifyComplete();
  }

  @Test
  void testHandleServerWebInputException_withUnrecognizedProperty() {
    final var unrecognized = mock(UnrecognizedPropertyException.class);
    when(unrecognized.getPropertyName()).thenReturn("unknownField");

    final var inputException = mock(ServerWebInputException.class);
    when(inputException.getCause()).thenReturn(unrecognized);

    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/users");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.handleServerWebInputException(
        inputException,
        HttpHeaders.EMPTY,
        HttpStatus.BAD_REQUEST,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.message()).isEqualTo("Invalid request body");
          assertThat(body.errors()).hasSize(1);
          assertThat(body.errors().getFirst().field()).isEqualTo("unknownField");
        })
        .verifyComplete();
  }

  @Test
  void testHandleServerWebInputException_nestedUnrecognizedProperty() {
    final var unrecognized = mock(UnrecognizedPropertyException.class);
    when(unrecognized.getPropertyName()).thenReturn("nestedField");

    final var cause = mock(Exception.class);
    when(cause.getCause()).thenReturn(unrecognized);

    final var inputException = mock(ServerWebInputException.class);
    when(inputException.getCause()).thenReturn(cause);

    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/data");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.handleServerWebInputException(
        inputException,
        HttpHeaders.EMPTY,
        HttpStatus.BAD_REQUEST,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.errors()).hasSize(1);
          assertThat(body.errors().getFirst().field()).isEqualTo("nestedField");
        })
        .verifyComplete();
  }

  @Test
  void testCreateResponseEntity_withApiResponse() {
    final var apiResponse = ApiResponse.success(200, "Items retrieved", List.of("item1", "item2"));
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/items");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.createResponseEntity(
        apiResponse,
        HttpHeaders.EMPTY,
        HttpStatus.OK,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(response.getBody()).isEqualTo(apiResponse);
        })
        .verifyComplete();
  }

  @Test
  void testCreateResponseEntity_withStringBody() {
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.createResponseEntity(
        "Error message",
        HttpHeaders.EMPTY,
        HttpStatus.INTERNAL_SERVER_ERROR,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.message()).isEqualTo("Error message");
        })
        .verifyComplete();
  }

  @Test
  void testCreateResponseEntity_withNullBody() {
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.createResponseEntity(
        null,
        HttpHeaders.EMPTY,
        HttpStatus.NOT_FOUND,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.status()).isEqualTo(404);
        })
        .verifyComplete();
  }

  @Test
  void testHandleConstraintViolation() {
    final var violation = mock(ConstraintViolation.class);
    final var path = mock(jakarta.validation.Path.class);
    when(path.toString()).thenReturn("email");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must be a valid email format");

    final Set<ConstraintViolation<?>> violations = new HashSet<>();
    violations.add(violation);

    final var exception = new ConstraintViolationException(violations);
    final var request = mock(ServerHttpRequest.class);
    final var requestPath = mock(org.springframework.http.server.RequestPath.class);
    when(requestPath.value()).thenReturn("/api/users");
    when(request.getPath()).thenReturn(requestPath);

    final var response = handler.handleConstraintViolation(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.errors()).hasSize(1);
    assertThat(body.errors().getFirst().field()).isEqualTo("email");
    assertThat(body.errors().getFirst().message()).isEqualTo("must be a valid email format");
  }

  @Test
  void testHandleConstraintViolation_multipleViolations() {
    final var violation1 = mock(ConstraintViolation.class);
    final var path1 = mock(jakarta.validation.Path.class);
    when(path1.toString()).thenReturn("username");
    when(violation1.getPropertyPath()).thenReturn(path1);
    when(violation1.getMessage()).thenReturn("must not be blank");

    final var violation2 = mock(ConstraintViolation.class);
    final var path2 = mock(jakarta.validation.Path.class);
    when(path2.toString()).thenReturn("password");
    when(violation2.getPropertyPath()).thenReturn(path2);
    when(violation2.getMessage()).thenReturn("must be at least 8 characters");

    final Set<ConstraintViolation<?>> violations = new HashSet<>();
    violations.add(violation1);
    violations.add(violation2);

    final var exception = new ConstraintViolationException(violations);
    final var request = mock(ServerHttpRequest.class);
    final var requestPath = mock(org.springframework.http.server.RequestPath.class);
    when(requestPath.value()).thenReturn("/api/register");
    when(request.getPath()).thenReturn(requestPath);

    final var response = handler.handleConstraintViolation(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.errors()).hasSize(2);
  }

  @Test
  void testHandleUnrecognizedProperty() {
    final var exception = mock(UnrecognizedPropertyException.class);
    when(exception.getPropertyName()).thenReturn("unknownField");

    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/data");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleUnrecognizedProperty(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEqualTo("Invalid request body");
    assertThat(body.errors()).hasSize(1);
    assertThat(body.errors().getFirst().field()).isEqualTo("unknownField");
    assertThat(body.errors().getFirst().message()).isEqualTo("Unknown field: 'unknownField'");
  }

  @Test
  void testHandleIllegalArgument_withMessage() {
    final var exception = new IllegalArgumentException("Invalid argument provided");
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/process");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalArgument(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(400);
    assertThat(body.message()).isEqualTo("Invalid argument provided");
  }

  @Test
  void testHandleIllegalArgument_emptyMessage() {
    final var exception = new IllegalArgumentException("");
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/process");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalArgument(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEmpty();
  }

  @Test
  void testHandleIllegalState_withMessage() {
    final var exception = new IllegalStateException("Invalid state detected");
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/state");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalState(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(500);
    assertThat(body.message()).isEqualTo("Invalid state detected");
  }

  @Test
  void testHandleIllegalState_emptyMessage() {
    final var exception = new IllegalStateException("");
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/state");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalState(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEmpty();
  }

  @Test
  void testHandleGenericException() {
    final var exception = new RuntimeException("Unexpected error");
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleGenericException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo(500);
    assertThat(body.message()).isEqualTo("An unexpected error occurred: Unexpected error");
  }

  @Test
  void testHandleGenericException_nullMessage() {
    final var exception = new RuntimeException((String) null);
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleGenericException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEqualTo("An unexpected error occurred: null");
  }

  @Test
  void testFindCause_withDirectMatch() {
    final var illegalStateException = new IllegalStateException("Test state");
    final var runtimeException = new RuntimeException(illegalStateException);

    final var result = findCauseViaReflection(runtimeException, IllegalStateException.class);

    assertThat(result).isEqualTo(illegalStateException);
  }

  @Test
  void testFindCause_withDeepChain() {
    final var illegalArgumentException = new IllegalArgumentException("Test argument");
    final var illegalStateException = new IllegalStateException(illegalArgumentException);
    final var runtimeException = new RuntimeException(illegalStateException);

    final var result = findCauseViaReflection(runtimeException, IllegalArgumentException.class);

    assertThat(result).isEqualTo(illegalArgumentException);
  }

  @Test
  void testFindCause_withNoMatch() {
    final var exception = new RuntimeException("Test");

    final var result = findCauseViaReflection(exception, IllegalArgumentException.class);

    assertThat(result).isNull();
  }

  @Test
  void testFindCause_withNullCause() {
    final var exception = new RuntimeException("Test");

    final var result = findCauseViaReflection(exception, RuntimeException.class);

    assertThat(result).isEqualTo(exception);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Some error message", "Another exception"})
  void testHandleIllegalArgument_variousMessages(final String message) {
    final var exception = new IllegalArgumentException(message);
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalArgument(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEqualTo(message);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "State error", "Invalid operation"})
  void testHandleIllegalState_variousMessages(final String message) {
    final var exception = new IllegalStateException(message);
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var response = handler.handleIllegalState(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    @SuppressWarnings("unchecked")
    final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.message()).isEqualTo(message);
  }

  @Test
  void testCreateResponseEntity_statusCodes() {
    final var request = mock(ServerHttpRequest.class);
    final var path = mock(org.springframework.http.server.RequestPath.class);
    when(path.value()).thenReturn("/api/test");
    when(request.getPath()).thenReturn(path);

    final var exchange = mock(ServerWebExchange.class);
    when(exchange.getRequest()).thenReturn(request);

    final var result = handler.createResponseEntity(
        "Conflict error",
        HttpHeaders.EMPTY,
        HttpStatus.CONFLICT,
        exchange);

    StepVerifier.create(result)
        .assertNext(response -> {
          assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          @SuppressWarnings("unchecked")
          final ApiResponse<Object> body = (ApiResponse<Object>) response.getBody();
          assertThat(body).isNotNull();
          assertThat(body.status()).isEqualTo(409);
          assertThat(body.error()).isEqualTo("Conflict");
        })
        .verifyComplete();
  }

  @Test
  void testFindCause_withThrowableAtRoot() {
    final var throwable = new RuntimeException("Root cause");

    final var result = findCauseViaReflection(throwable, RuntimeException.class);

    assertThat(result).isEqualTo(throwable);
  }

  @Test
  void testFindCause_doesNotMatchClass() {
    final var throwable = new RuntimeException("Test");

    final var result = findCauseViaReflection(throwable, IllegalArgumentException.class);

    assertThat(result).isNull();
  }


  @Test
  void testFindCause_withOneElementThrowableChain() {
    final var exception = new IllegalStateException("Single exception");

    final var result = findCauseViaReflection(exception, IllegalStateException.class);

    assertThat(result).isEqualTo(exception);
  }

  @Test
  void testFindCause_withNullThrowable() {
    final var result = findCauseViaReflection(null, RuntimeException.class);

    assertThat(result).isNull();
  }

  @Test
  void testFindCause_exitsDueToFoundNotNull() {
    final var targetException = new IllegalArgumentException("Target");
    final var deepCause = new RuntimeException("Deep");
    final var intermediateCause = new IllegalStateException(deepCause);
    final var rootCause = new RuntimeException("Root", intermediateCause);

    final var result = findCauseViaReflection(rootCause, IllegalStateException.class);

    assertThat(result).isEqualTo(intermediateCause);
    assertThat(result.getCause()).isEqualTo(deepCause);
  }

  private <T extends Throwable> T findCauseViaReflection(
      final Throwable throwable, final Class<T> type) {
    if (throwable == null) {
      return null;
    }
    Throwable cause = throwable;
    T found = null;
    while (cause != null && found == null) {
      if (type.isInstance(cause)) {
        @SuppressWarnings("unchecked")
        final T result = (T) cause;
        found = result;
      } else {
        cause = cause.getCause();
      }
    }
    return found;
  }
}

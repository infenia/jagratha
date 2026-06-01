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
package com.infenia.yukta.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

@ExtendWith(MockitoExtension.class)
class CliFormatterTest {

  @Mock private ObjectMapper objectMapper;
  @Mock private ObjectWriter objectWriter;

  private CliFormatter formatter;

  @BeforeEach
  void setUpForMockedTests() {
    when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
    formatter = new CliFormatter(objectMapper);
  }

  @Test
  void formatAsJson_formatsObjectAsJson() throws Exception {
    final Object testObject = new Object();
    final String expectedJson = "{\"test\": \"value\"}";
    when(objectWriter.writeValueAsString(testObject)).thenReturn(expectedJson);

    final String result = formatter.formatAsJson(testObject);

    assertThat(result).isEqualTo(expectedJson);
  }

  @Test
  void formatAsJson_returnsFormattedString() throws Exception {
    final String testData = "test";
    final String expected = "\"test\"";
    when(objectWriter.writeValueAsString(testData)).thenReturn(expected);

    final String result = formatter.formatAsJson(testData);

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void formatAsJson_throwsExceptionWhenObjectMapperFails() throws Exception {
    final Object testObject = new Object();
    when(objectWriter.writeValueAsString(testObject))
        .thenThrow(new RuntimeException("Serialization failed"));

    assertThatThrownBy(() -> formatter.formatAsJson(testObject))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Serialization failed");
  }

  @Test
  void printJson_printsFormattedJson() throws Exception {
    final Object testObject = new Object();
    final String expectedJson = "{\"test\": \"value\"}";
    when(objectWriter.writeValueAsString(testObject)).thenReturn(expectedJson);

    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capture));

    try {
      formatter.printJson(testObject);

      final String output = capture.toString();
      assertThat(output).contains(expectedJson);
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void printJson_throwsExceptionWhenFormattingFails() throws Exception {
    final Object testObject = new Object();
    when(objectWriter.writeValueAsString(testObject))
        .thenThrow(new RuntimeException("Formatting failed"));

    assertThatThrownBy(() -> formatter.printJson(testObject))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Formatting failed");
  }
}

class CliFormatterPrintTableTest {

  private CliFormatter formatter;

  @BeforeEach
  void setUp() {
    formatter = new CliFormatter(new ObjectMapper());
  }

  @Test
  void printTable_printsAllItems() {
    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capture));

    try {
      final List<String> items = List.of("item1", "item2", "item3");
      formatter.printTable(items);

      final String output = capture.toString();
      assertThat(output).contains("item1").contains("item2").contains("item3");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void printTable_printsItemsWithNewlines() {
    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capture));

    try {
      final List<String> items = List.of("line1", "line2");
      formatter.printTable(items);

      final String output = capture.toString();
      assertThat(output).contains("line1\n").contains("line2\n");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void printTable_handlesEmptyList() {
    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capture));

    try {
      formatter.printTable(List.of());

      final String output = capture.toString();
      assertThat(output).isEmpty();
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  void printTable_singleItem() {
    final PrintStream originalOut = System.out;
    final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capture));

    try {
      formatter.printTable(List.of("single"));

      final String output = capture.toString();
      assertThat(output).contains("single");
    } finally {
      System.setOut(originalOut);
    }
  }
}

class CliFormatterConstructorTest {

  @Test
  void constructor_storesObjectMapper() {
    final ObjectMapper mapper = new ObjectMapper();
    final CliFormatter testFormatter = new CliFormatter(mapper);

    assertThat(testFormatter).isNotNull();
  }
}

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

import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CliFormatter {

  private final ObjectMapper objectMapper;

  public CliFormatter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String formatAsJson(Object value) throws Exception {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
  }

  public void printTable(List<String> items) {
    items.forEach(System.out::println);
  }

  public void printJson(Object value) throws Exception {
    System.out.println(formatAsJson(value));
  }
}

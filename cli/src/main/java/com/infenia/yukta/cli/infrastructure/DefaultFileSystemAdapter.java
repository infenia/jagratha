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
package com.infenia.yukta.cli.infrastructure;

import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DefaultFileSystemAdapter implements FileSystemAdapter {

  @Override
  public void createDirectories(Path path) throws Exception {
    Files.createDirectories(path);
  }

  @Override
  public void writeString(Path path, String content, OpenOption... options) throws Exception {
    Files.writeString(path, content, options);
  }

  @Override
  public String readString(Path path) throws Exception {
    return Files.readString(path);
  }

  @Override
  public void delete(Path path) throws Exception {
    Files.delete(path);
  }

  @Override
  public boolean deleteIfExists(Path path) throws Exception {
    return Files.deleteIfExists(path);
  }

  @Override
  public boolean exists(Path path) {
    return Files.exists(path);
  }
}

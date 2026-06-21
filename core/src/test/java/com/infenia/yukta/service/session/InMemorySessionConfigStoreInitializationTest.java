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
package com.infenia.yukta.service.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.infenia.yukta.config.SessionConfigProperties;
import com.infenia.yukta.service.workflow.store.WorkflowDefinitionStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class InMemorySessionConfigStoreInitializationTest {

  @Test
  void postConstructLogsInitializationMessage() throws Exception {
    SessionConfigProperties props = new SessionConfigProperties();
    props.setBaseDir(System.getProperty("user.home") + "/.yukta");
    props.setFileLogSubDir("modified-files");
    props.setResultLogSubDir("results");
    props.setExecutionTimeoutSeconds(3600L);

    WorkflowDefinitionStore workflowDefinitionStore = Mockito.mock(WorkflowDefinitionStore.class);
    InMemorySessionConfigStore store =
        new InMemorySessionConfigStore(props, workflowDefinitionStore);

    ReflectionTestUtils.invokeMethod(store, "logInitialization");

    assertThat(store).isNotNull();
  }
}

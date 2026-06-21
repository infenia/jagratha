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

import com.infenia.yukta.config.SessionConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for providing the configured SessionConfigStore instance. Store selection is delegated to
 * Spring's conditional bean creation based on yukta.session.store-type property.
 */
@Slf4j
@Component
public class SessionConfigStoreFactory {

  private final SessionConfigStore configuredStore;
  private final SessionConfigProperties props;

  /**
   * Constructs the factory with the conditionally-created store implementation.
   *
   * @param configuredStore the store implementation selected by Spring based on configuration
   * @param props the session configuration properties
   */
  @Autowired
  public SessionConfigStoreFactory(
      final SessionConfigStore configuredStore, final SessionConfigProperties props) {
    this.configuredStore = configuredStore;
    this.props = props;
  }

  /**
   * Get the configured SessionConfigStore based on the store-type property.
   *
   * @return the configured SessionConfigStore instance
   */
  public SessionConfigStore getStore() {
    final String storeType = props.getStoreType();
    log.info("Using SessionConfigStore with type: {}", storeType);
    return configuredStore;
  }
}

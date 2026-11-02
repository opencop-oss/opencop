// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface RawEventHandler<I> {

  void handle(List<ConsumerRecord<String, I>> message);
}

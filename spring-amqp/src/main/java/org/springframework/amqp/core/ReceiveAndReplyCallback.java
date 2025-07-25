/*
 * Copyright 2014-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.amqp.core;

import org.jspecify.annotations.Nullable;

/**
 * To be used with the receive-and-reply methods of {@link org.springframework.amqp.core.AmqpTemplate}
 * as processor for inbound object and producer for outbound object.
 *
 * <p>This often as an anonymous class within a method implementation.
 * @param <R> The type of the request after conversion from the {@link Message}.
 * @param <S> The type of the response.
 *
 * @author Artem Bilan
 * @author Gary Russell
 *
 * @since 1.3
 */
@FunctionalInterface
public interface ReceiveAndReplyCallback<R, S> {

	@Nullable
	S handle(R payload);

}

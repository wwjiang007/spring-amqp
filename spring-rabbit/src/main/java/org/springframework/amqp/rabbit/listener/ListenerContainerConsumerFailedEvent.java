/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.amqp.rabbit.listener;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

import org.springframework.amqp.event.AmqpEvent;

/**
 * Published when a listener consumer fails.
 *
 * @author Gary Russell
 * @author Artem Bilan
 *
 * @since 1.5
 *
 */
public class ListenerContainerConsumerFailedEvent extends AmqpEvent {

	@Serial
	private static final long serialVersionUID = -8122166328567190605L;

	private final @Nullable String reason;

	private final boolean fatal;

	private final @Nullable Throwable throwable;

	/**
	 * Construct an instance with the provided arguments.
	 * @param source the source container.
	 * @param reason the reason.
	 * @param throwable the throwable.
	 * @param fatal true if the startup failure was fatal (will not be retried).
	 */
	public ListenerContainerConsumerFailedEvent(Object source, @Nullable String reason,
			@Nullable Throwable throwable, boolean fatal) {
		super(source);
		this.reason = reason;
		this.fatal = fatal;
		this.throwable = throwable;
	}

	public @Nullable String getReason() {
		return this.reason;
	}

	public boolean isFatal() {
		return this.fatal;
	}

	public @Nullable Throwable getThrowable() {
		return this.throwable;
	}

	@Override
	public String toString() {
		return "ListenerContainerConsumerFailedEvent [reason=" + this.reason + ", fatal=" + this.fatal + ", throwable="
				+ this.throwable + ", container=" + this.source + "]";
	}

}

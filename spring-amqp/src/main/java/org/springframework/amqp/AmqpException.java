/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.amqp;

import org.jspecify.annotations.Nullable;

/**
 * Base RuntimeException for errors that occur when executing AMQP operations.
 *
 * @author Mark Fisher
 * @author Artem Bilan
 */
@SuppressWarnings("serial")
public class AmqpException extends RuntimeException {

	public AmqpException(String message) {
		super(message);
	}

	public AmqpException(@Nullable Throwable cause) {
		super(cause);
	}

	public AmqpException(@Nullable String message, @Nullable Throwable cause) {
		super(message, cause);
	}

}

/*
 * Copyright 2021-present the original author or authors.
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

package org.springframework.rabbit.stream.listener;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler.Context;
import com.rabbitmq.stream.OffsetSpecification;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.junit.AbstractTestContainerTests;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.rabbit.stream.config.StreamRabbitListenerContainerFactory;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;
import org.springframework.rabbit.stream.retry.StreamRetryOperationsInterceptorFactoryBean;
import org.springframework.rabbit.stream.support.StreamAdmin;
import org.springframework.rabbit.stream.support.StreamMessageProperties;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.4
 *
 */
@SpringJUnitConfig
@DirtiesContext
public class RabbitListenerTests extends AbstractTestContainerTests {

	@Autowired
	Config config;

	@Test
	void simple(@Autowired RabbitStreamTemplate template, @Autowired MeterRegistry meterRegistry) throws Exception {
		Future<Boolean> future = template.convertAndSend("foo");
		assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
		future = template.convertAndSend("bar", msg -> msg);
		assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
		future = template.send(new org.springframework.amqp.core.Message("baz".getBytes(),
				new StreamMessageProperties()));
		assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
		future = template.send(template.messageBuilder().addData("qux".getBytes()).build());
		assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
		assertThat(this.config.latch1.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(this.config.received).containsExactly("foo", "foo", "bar", "baz", "qux");
		assertThat(this.config.id).isEqualTo("testNative");
		MeterRegistryAssert.assertThat(meterRegistry)
				.hasTimerWithNameAndTags("spring.rabbit.stream.template",
						KeyValues.of("spring.rabbit.stream.template.name", "streamTemplate1"))
				.hasTimerWithNameAndTags("spring.rabbit.stream.listener",
						KeyValues.of("spring.rabbit.stream.listener.id", "obs"))
				.hasTimerWithNameAndTags("spring.rabbitmq.listener",
						KeyValues.of("listener.id", "notObs")
								.and("queue", "test.stream.queue1"));
	}

	@Test
	void nativeMsg(@Autowired RabbitTemplate template, @Autowired MeterRegistry meterRegistry)
			throws InterruptedException {

		template.convertAndSend("test.stream.queue2", "foo");
		// Send a second to ensure the timer exists before the assertion
		template.convertAndSend("test.stream.queue2", "bar");
		assertThat(this.config.latch2.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(this.config.receivedNative).isNotNull();
		assertThat(this.config.context).isNotNull();
		assertThat(this.config.latch3.await(10, TimeUnit.SECONDS)).isTrue();
		assertThat(this.config.latch4.await(10, TimeUnit.SECONDS)).isTrue();
		MeterRegistryAssert.assertThat(meterRegistry)
				.hasTimerWithNameAndTags("spring.rabbit.stream.listener",
						KeyValues.of("spring.rabbit.stream.listener.id", "testObsNative"))
				.hasTimerWithNameAndTags("spring.rabbitmq.listener",
						KeyValues.of("listener.id", "testNative"))
				.hasTimerWithNameAndTags("spring.rabbitmq.listener",
						KeyValues.of("listener.id", "testNativeFail"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void queueOverAmqp() throws Exception {
		WebClient client = WebClient.builder()
				.filter(ExchangeFilterFunctions.basicAuthentication("guest", "guest"))
				.build();
		Map<String, Object> queue = queueInfo("stream.created.over.amqp");
		assertThat(((Map<String, Object>) queue.get("arguments")).get("x-queue-type")).isEqualTo("stream");
	}

	private Map<String, Object> queueInfo(String queueName) throws URISyntaxException {
		WebClient client = createClient("guest", "guest");
		URI uri = queueUri(queueName);
		return client.get()
				.uri(uri)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
				})
				.block(Duration.ofSeconds(10));
	}

	private URI queueUri(String queue) throws URISyntaxException {
		return new URI("http://localhost:" + managementPort() + "/api")
				.resolve("/api/queues/" + UriUtils.encodePathSegment("/", StandardCharsets.UTF_8) + "/" + queue);
	}

	private WebClient createClient(String adminUser, String adminPassword) {
		return WebClient.builder()
				.filter(ExchangeFilterFunctions.basicAuthentication(adminUser, adminPassword))
				.build();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRabbit
	public static class Config {

		final CountDownLatch latch1 = new CountDownLatch(9);

		final CountDownLatch latch2 = new CountDownLatch(4);

		final CountDownLatch latch3 = new CountDownLatch(6);

		final CountDownLatch latch4 = new CountDownLatch(1);

		final List<String> received = new ArrayList<>();

		final AtomicBoolean first = new AtomicBoolean(true);

		volatile Message receivedNative;

		volatile Context context;

		volatile String id;

		@Bean
		MeterRegistry meterReg() {
			return new SimpleMeterRegistry();
		}

		@Bean
		ObservationRegistry obsReg(MeterRegistry meterRegistry) {
			ObservationRegistry registry = ObservationRegistry.create();
			registry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meterRegistry));
			return registry;
		}

		@Bean
		static Environment environment() {
			return Environment.builder()
					.port(streamPort())
					.build();
		}

		@Bean
		StreamAdmin streamAdmin(Environment env) {
			StreamAdmin streamAdmin = new StreamAdmin(env, sc -> {
				sc.stream("test.stream.queue1").create();
				sc.stream("test.stream.queue2").create();
			});
			streamAdmin.setAutoStartup(false);
			return streamAdmin;
		}

		@Bean
		SmartLifecycle creator(Environment env, StreamAdmin admin) {
			return new SmartLifecycle() {

				boolean running;

				@Override
				public void stop() {
					clean(env);
					this.running = false;
				}

				@Override
				public void start() {
					clean(env);
					admin.start();
					this.running = true;
				}

				private void clean(Environment env) {
					try {
						env.deleteStream("test.stream.queue1");
					}
					catch (Exception e) {
					}
					try {
						env.deleteStream("test.stream.queue2");
					}
					catch (Exception e) {
					}
					try {
						env.deleteStream("stream.created.over.amqp");
					}
					catch (Exception e) {
					}
				}

				@Override
				public boolean isRunning() {
					return this.running;
				}

				@Override
				public int getPhase() {
					return 0;
				}

			};
		}

		@Bean
		RabbitListenerContainerFactory<StreamListenerContainer> rabbitListenerContainerFactory(Environment env) {
			StreamRabbitListenerContainerFactory factory = new StreamRabbitListenerContainerFactory(env);
			factory.setAdviceChain(RetryInterceptorBuilder.stateless().build());
			return factory;
		}

		@Bean
		RabbitListenerContainerFactory<StreamListenerContainer> observableFactory(Environment env) {
			StreamRabbitListenerContainerFactory factory = new StreamRabbitListenerContainerFactory(env);
			factory.setObservationEnabled(true);
			factory.setConsumerCustomizer((id, builder) -> {
				builder.name(id)
						.offset(OffsetSpecification.first())
						.manualTrackingStrategy();
			});
			return factory;
		}

		@RabbitListener(id = "notObs", queues = "test.stream.queue1")
		void listen(String in) {
			this.received.add(in);
			this.latch1.countDown();
			if (first.getAndSet(false)) {
				throw new RuntimeException("fail first");
			}
		}

		@RabbitListener(id = "obs", queues = "test.stream.queue1", containerFactory = "observableFactory")
		void listenObs(String in) {
			this.latch1.countDown();
		}

		@Bean
		public StreamRetryOperationsInterceptorFactoryBean sfb() {
			StreamRetryOperationsInterceptorFactoryBean rfb = new StreamRetryOperationsInterceptorFactoryBean();
			rfb.setStreamMessageRecoverer((msg, context, throwable) -> this.latch4.countDown());
			return rfb;
		}

		@Bean
		@DependsOn("sfb")
		RabbitListenerContainerFactory<StreamListenerContainer> nativeFactory(Environment env,
				RetryOperationsInterceptor retry) {

			StreamRabbitListenerContainerFactory factory = new StreamRabbitListenerContainerFactory(env);
			factory.setNativeListener(true);
			factory.setConsumerCustomizer((id, builder) -> {
				builder.name(id)
						.offset(OffsetSpecification.first())
						.manualTrackingStrategy();
				if (id.equals("testNative")) {
					this.id = id;
				}
			});
			factory.setAdviceChain(retry);
			return factory;
		}

		@Bean
		RabbitListenerContainerFactory<StreamListenerContainer> nativeObsFactory(Environment env,
				RetryOperationsInterceptor retry) {

			StreamRabbitListenerContainerFactory factory = new StreamRabbitListenerContainerFactory(env);
			factory.setNativeListener(true);
			factory.setObservationEnabled(true);
			factory.setConsumerCustomizer((id, builder) -> {
				builder.name(id)
						.offset(OffsetSpecification.first())
						.manualTrackingStrategy();
			});
			return factory;
		}

		@RabbitListener(id = "testNative", queues = "test.stream.queue2", containerFactory = "nativeFactory")
		void nativeMsg(Message in, Context context) {
			this.receivedNative = in;
			this.context = context;
			this.latch2.countDown();
			context.storeOffset();
		}

		@RabbitListener(id = "testNativeFail", queues = "test.stream.queue2", containerFactory = "nativeFactory")
		void nativeMsgFail(Message in, Context context) {
			this.latch3.countDown();
			throw new RuntimeException("fail all");
		}

		@RabbitListener(id = "testObsNative", queues = "test.stream.queue2", containerFactory = "nativeObsFactory")
		void nativeObsMsg(Message in, Context context) {
			this.receivedNative = in;
			this.context = context;
			this.latch2.countDown();
			context.storeOffset();
		}

		@Bean
		CachingConnectionFactory cf() {
			return new CachingConnectionFactory("localhost", amqpPort());
		}

		@Bean
		RabbitTemplate template(CachingConnectionFactory cf) {
			return new RabbitTemplate(cf);
		}

		@Bean
		RabbitStreamTemplate streamTemplate1(Environment env) {
			RabbitStreamTemplate template = new RabbitStreamTemplate(env, "test.stream.queue1");
			template.setProducerCustomizer((name, builder) -> builder.name("test"));
			template.setObservationEnabled(true);
			return template;
		}

		@Bean
		RabbitAdmin admin(CachingConnectionFactory cf) {
			return new RabbitAdmin(cf);
		}

		@Bean
		Queue queue() {
			return QueueBuilder.durable("stream.created.over.amqp")
					.stream()
					.build();
		}

	}

}

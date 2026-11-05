package vn.mta.shop.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisClientConfig {
  @Bean
  LettuceClientConfigurationBuilderCustomizer boundedRedisQueue(RedisProperties properties) {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(
                    SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build())
                .timeoutOptions(TimeoutOptions.enabled(properties.getTimeout()))
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .requestQueueSize(64)
                .build());
  }
}

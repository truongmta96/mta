package vn.mta.shop.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Giới hạn thời gian/tài nguyên ở Redis client; service bắt lỗi client để chuyển sang MySQL.
 * Timeout cụ thể lấy từ spring.data.redis trong application.yml (mặc định 200 ms).
 */
@Configuration
public class RedisClientConfig {
  @Bean
  LettuceClientConfigurationBuilderCustomizer boundedRedisQueue(RedisProperties properties) {
    return builder ->
        builder.clientOptions(
            ClientOptions.builder()
                // Lettuce tự kết nối lại ở nền khi Redis hoạt động trở lại.
                .autoReconnect(true)
                // Timeout mở kết nối TCP; phải giữ lại khi thay ClientOptions mặc định của Boot.
                .socketOptions(
                    SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build())
                // Timeout lệnh: TCP có thể còn mở nhưng Redis xử lý chậm hoặc không trả lời.
                .timeoutOptions(TimeoutOptions.enabled(properties.getTimeout()))
                // Mất kết nối thì báo lỗi sớm để service fallback, không đợi lệnh trong hàng chờ.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                // Chặn hàng chờ tăng không giới hạn khi nhiều request gặp Redis chậm/hỏng.
                .requestQueueSize(64)
                .build());
  }
}

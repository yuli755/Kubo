package cn.hamm.spms.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * <h1>RedisTemplate 配置（{@code RedisTemplate<String, Object>} 的唯一提供方）</h1>
 *
 * <p><b>这个 bean 为什么必须存在（B0 静默丢失的第四例）</b>：全仓 Redis 消费方
 * （{@code RedisHelper} 及其下游：登录防爆破、限流、重置防爆破、数据权限事件、
 * cookie/权限缓存）注入的都是 {@code RedisTemplate<String, Object>}，而 Spring Boot
 * 自动配置只供给 {@code RedisTemplate<Object, Object>} —— 泛型不匹配时按类型无候选，
 * 注入点又写了 {@code @Autowired(required = false)}（为本地无 Redis 调试留的降级口），
 * 于是注入被<b>静默</b>填成 {@code null}：编译过、启动过、单测（全 mock）全绿，
 * 只有真机上 MONITOR 才能看见「应用对 Redis 一条命令都没发」。</p>
 *
 * <p>历史上该 bean 由 {@code cn.hamm:airpower-redis} 的 {@code RedisConfig} 提供；
 * B0 父 POM 退场时依赖摘了、消费方代码复制进了本仓，配置类却没跟过来——与
 * {@code -parameters}（pom 注释第三例）同族：<b>丢的都是不报错的配置</b>。</p>
 *
 * <p>bean 命名为 {@code redisTemplate}：Boot 的 {@code RedisAutoConfiguration#redisTemplate}
 * 带 {@code @ConditionalOnMissingBean(name = "redisTemplate")}，本 bean 在场时自动配置
 * 干净退让，容器里始终只有一个 RedisTemplate，消费方按名字命中。</p>
 *
 * <p>序列化口径对齐 {@link RedisCacheConfig}：key 用 {@link StringRedisSerializer}
 * （redis-cli 里键名肉眼可读，运维可直接 KEYS/GET 排障），value 用带类型信息的 JSON
 * （{@code get} 回读仍是原类型，cookie→userId 等缓存值语义不变）。</p>
 *
 * <p><b>装配门槛</b>：仅当配置了 {@code spring.data.redis.host} 才注册本 bean。
 * local / production 都显式写了该键；测试档（application-test.yml）刻意不写——
 * {@code @SpringBootTest} 那批 H2 测试此前全靠「template 为 null」静默降级跑绿，
 * 门槛保证 bean 在场与否跟随部署意图，测试面零惊扰。</p>
 *
 * @author Hamm.cn
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisTemplateConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // JSON 里写入 @class 类型信息：Object 值回读保持原类型（GenericJackson2 的常规做法）
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}

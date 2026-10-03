package cn.hamm.spms.config;

import cn.hamm.spms.common.redis.RedisHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>装配哨兵：Redis 配置在场时 {@code RedisTemplate<String, Object>} 必须真实注入</h1>
 *
 * <p><b>钉住的是 B0 第四处静默丢失这一类缺陷</b>（报告 §20.3）：全仓消费方注入的都是
 * {@code RedisTemplate<String, Object>}，Boot 自动配置只供 {@code RedisTemplate<Object, Object>}，
 * 泛型不匹配 + {@code @Autowired(required = false)} 的组合让注入静默变 null——编译过、启动过、
 * 940 个全 mock 的单测全绿，只有真机 MONITOR 能看见「应用对 Redis 一条命令都没发」。
 * 本哨兵把「装配在场」升格为容器级断言：起了容器、配了 host、拿到的 template 却是 null，
 * 这里立刻红，不必等真机。</p>
 *
 * <p>连接池是惰性的：设 {@code spring.data.redis.host} 只创建
 * {@code LettuceConnectionFactory} bean，<b>不会</b>在启动期发起连接——所以本测试不需要
 * 真实 Redis 在场，钉的是装配图而非连通性（连通性归真机冒烟）。</p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none",
                "spring.data.redis.host=127.0.0.1",
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@DisplayName("装配哨兵 · redis host 在场 → template 必须非 null（B0 第四例回归锁）")
class RedisTemplateWiringSentinelTest {

    @Autowired
    private RedisHelper redisHelper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("RedisHelper 拿到的 template 非 null，且与容器 bean 同一个")
    void templateIsWiredIntoRedisHelper() {
        RedisTemplate<String, Object> wired = redisHelper.getRedisTemplate();

        assertNotNull(wired,
                "配置了 spring.data.redis.host 却拿到 null —— 这就是 B0 第四处静默丢失的形态："
                        + "RedisTemplateConfig 的 provider 丢了，消费方全部静默降级而无人报错");
        assertSame(redisTemplate, wired, "RedisHelper 注入的必须是容器里那个 redisTemplate");
    }

    @Test
    @DisplayName("序列化口径：key 用 String（redis-cli 可读），value 用带类型 JSON")
    void serializerContractHolds() {
        assertTrue(redisTemplate.getKeySerializer() instanceof StringRedisSerializer,
                "key 序列化必须让 redis-cli 里键名肉眼可读（运维 KEYS/GET 排障的前提）");
        assertTrue(redisTemplate.getValueSerializer() instanceof GenericJackson2JsonRedisSerializer,
                "value 必须走带 @class 类型信息的 JSON（cookie→userId 等缓存值回读语义不变）");
    }
}

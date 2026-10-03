package cn.hamm.spms.config;

import cn.hamm.spms.common.redis.RedisHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>装配哨兵（负向半边）：测试档未配 redis host → template 必须是刻意的 null</h1>
 *
 * <p>{@link RedisTemplateConfig} 的 {@code @ConditionalOnProperty} 门控保证「bean 在场与否
 * 跟随部署意图」：测试档（application-test.yml 不写 host）里 template 为 null，全部 H2
 * {@code @SpringBootTest} 靠降级路径跑绿。这个 null 是<b>声明过</b>的部署形态（与 B0 的
 * 事故性 null 的边界，报告 §21.4 最后一条）。</p>
 *
 * <p>本测试钉住门控本身：若有人把 bean 改成无条件注册（去掉 {@code @ConditionalOnProperty}），
 * 测试档就会开始构造 RedisTemplate、消费方的降级分支被静默翻转——这里立刻红。</p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = "spring.cache.type=none",
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@DisplayName("装配哨兵 · 未配 redis host → template 是刻意的 null（门控回归锁）")
class RedisTemplateAbsentWiringSentinelTest {

    @Autowired
    private RedisHelper redisHelper;

    @Test
    @DisplayName("测试档不写 spring.data.redis.host：RedisHelper 必须静默降级为 null")
    void templateAbsentWithoutHostProperty() {
        assertNull(redisHelper.getRedisTemplate(),
                "测试档里 template 应为 null（@ConditionalOnProperty 门控的刻意降级形态）；"
                        + "若非 null，说明 bean 变成无条件注册，消费方 null 分支被静默绕过");
    }
}

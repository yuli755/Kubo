package cn.hamm.spms;

import cn.hamm.airpower.core.TaskUtil;
import cn.hamm.airpower.redis.RedisHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local-hamm")
@Disabled("需要本地 Redis 与 local-hamm profile（还会真起一个 Spring 上下文并 sleep 20s）；"
        + "它验证的是 airpower 的分布式锁，不是本项目逻辑。要验证请本地跑，CI 里必红。")
public class ApplicationTest {
    // 显式声明 logger，不用 @Slf4j：main 侧 lombok 生成是正常的（40 个 @Slf4j 类、@Data 实体都没显式 getter），
    // 但 test-compile 里 lombok 没生效（CI 报 `cannot find symbol: log`，原因待查，见 docs §19.9 的 CI 复盘）。
    // 一个手工验证用的测试类不值得为它去动构建配置，先把它的日志字段写成标准写法。
    private static final Logger log = LoggerFactory.getLogger(ApplicationTest.class);

    @Autowired
    private RedisHelper redisHelper;

    @Test
    public void lockTest() {
        log.info("test");
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            TaskUtil.runAsync(() -> test(finalI));
        }
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void test(int i) {
        log.info("{}", i);
        redisHelper.runWithLock("xxx_" + (i % 3), () -> {
            log.info("Locked {}", i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

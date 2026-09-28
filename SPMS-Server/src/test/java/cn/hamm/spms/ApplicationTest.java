package cn.hamm.spms;

import cn.hamm.spms.common.redis.RedisHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SpringBootTest
@ActiveProfiles("local-hamm")
@Disabled("需要本地 Redis 与 local-hamm profile；只用于人工启用的分布式锁探针，CI 不运行。")
public class ApplicationTest {
    // 显式声明 logger，不用 @Slf4j：main 侧 lombok 生成是正常的（40 个 @Slf4j 类、@Data 实体都没显式 getter），
    // 但 test-compile 里 lombok 没生效（CI 报 `cannot find symbol: log`，原因待查，见 docs §19.9 的 CI 复盘）。
    // 一个手工验证用的测试类不值得为它去动构建配置，先把它的日志字段写成标准写法。
    private static final Logger log = LoggerFactory.getLogger(ApplicationTest.class);

    @Autowired
    private RedisHelper redisHelper;

    @Test
    public void lockTest() throws Exception {
        var executor = Executors.newFixedThreadPool(10);
        try {
            var tasks = IntStream.range(0, 10).<Callable<Void>>mapToObj(i -> () -> {
                test(i);
                return null;
            }).toList();
            // 总等待上限 20 秒；任务异常/超时通过 Future 暴露，不能仅 sleep 后伪装成成功。
            for (var result : executor.invokeAll(tasks, 20, TimeUnit.SECONDS)) {
                result.get();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } finally {
            executor.shutdownNow();
        }
    }

    public void test(int i) {
        log.info("{}", i);
        // runWithLock 的签名是 (lockKey, timeoutSeconds, action)，此前漏传超时秒数导致编译不过。
        // 取 5 秒：与 InputService 等业务调用一致，且足够覆盖下面 1 秒的 sleep。
        redisHelper.runWithLock("xxx_" + (i % 3), 5, () -> {
            log.info("Locked {}", i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}

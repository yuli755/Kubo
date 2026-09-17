package cn.hamm.spms.core;

/*
 * 来源：AirPower-Core 6.3.1（https://github.com/AirPowerTeam/AirPower-Core ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 B2：整文件搬入本仓，仅把包名 cn.hamm.airpower.core → cn.hamm.spms.core，
 * 逻辑一字未改；本仓从此**自己拥有**这一层（上游变化不再自动进入）。改代码请改这里。
 * 为什么只搬这些：按 import 关系算出的**真实调用闭包**——本项目不用的（AesUtil/RsaUtil/HttpUtil/
 * HostUtil/ValidateUtil/DesensitizeUtil 等）一律不搬；上游其余部分仍由 airpower 依赖带着。
 */

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <h1>任务流程工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
public class TaskUtil {
    /**
     * 核心线程数（根据 CPU 核心数动态计算）
     */
    private static final int CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * 最大线程数
     */
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;

    /**
     * 线程池
     */
    @SuppressWarnings("AlibabaThreadShouldSetName")
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            60L,
            SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(@NotNull Runnable r) {
                    Thread thread = new Thread(r, "airpower-task-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 执行任务 {@code 不会抛出异常}
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     */
    public static void run(Runnable runnable, Runnable... moreRunnable) {
        getRunnableList(runnable, moreRunnable).forEach(run -> {
            try {
                run.run();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    /**
     * 异步执行任务 {@code 不会抛出异常}
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     */
    public static void runAsync(Runnable runnable, Runnable... moreRunnable) {
        getRunnableList(runnable, moreRunnable).forEach((run) -> EXECUTOR.submit(() -> {
            try {
                run.run();
            } catch (Exception e) {
                log.error("异步执行任务失败, {}", e.getMessage(), e);
            }
        }));
    }

    /**
     * 获取任务列表
     *
     * @param runnable     任务
     * @param moreRunnable 更多任务
     * @return 任务列表
     */
    private static @NotNull List<Runnable> getRunnableList(Runnable runnable, Runnable[] moreRunnable) {
        List<Runnable> runnableList = new ArrayList<>();
        runnableList.add(runnable);
        runnableList.addAll(Arrays.asList(moreRunnable));
        return runnableList;
    }
}

package cn.hamm.spms.framework.curd.helper;

/*
 * 来源：AirPower4J 6.4.3（https://github.com/AirPowerTeam/AirPower4J ，MIT License，Copyright (c) 2022 Hamm）
 * 2026-09-18 后端 airpower 退场 —【框架层自持】整模块搬入本仓，只改包名：
 *   cn.hamm.airpower.core  → cn.hamm.spms.core
 *   cn.hamm.airpower.api   → cn.hamm.spms.framework.api
 *   cn.hamm.airpower.curd  → cn.hamm.spms.framework.curd
 *   cn.hamm.airpower.redis → cn.hamm.spms.framework.redis
 * 代码逻辑一字未改（JPA→MyBatis-Plus 的持久化替换是紧随其后的独立一批，见 docs）。
 * 从此这一层由本仓拥有：改代码改这里，不再有"上游"。
 */

import cn.hamm.spms.core.TaskUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>事务助手类</h1>
 *
 * @author Hamm.cn
 */
@Service
public class TransactionHelper {
    /**
     * 开始执行一个包含若干方法的事务
     *
     * @param function 事务包含的方法集合体
     * @apiNote 如需无视异常执行多项任务，可使用 {@link TaskUtil#run(Runnable, Runnable...)} 或 {@link TaskUtil#runAsync(Runnable, Runnable...)}
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public void run(@NotNull Function function) {
        function.run();
    }

    @FunctionalInterface
    public interface Function {
        /**
         * 开始执行一个包含若干方法的事务
         */
        void run();
    }

}

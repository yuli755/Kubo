package cn.hamm.spms.framework.curd.base;

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

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>数据源接口</h1>
 *
 * @param <E> 实体
 * @author Hamm.cn
 */
@NoRepositoryBean
public interface ICurdRepository<E extends CurdEntity<E>> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
    /**
     * 加 {@code 写锁} 查询
     *
     * @param id ID
     * @return 实体
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    E getForUpdateById(Long id);
}

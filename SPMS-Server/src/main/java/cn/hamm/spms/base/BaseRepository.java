package cn.hamm.spms.base;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h1>自持数据源接口基类</h1>
 *
 * <p>提供 Spring Data JPA 基础持久化、动态条件查询以及悲观排他行锁查询能力。
 * 替代旧 {@code ICurdRepository}。</p>
 *
 * @param <E> 实体类型
 * @author Hamm.cn
 */
@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity<E>> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
    /**
     * 加写锁查询实体（悲观排他锁，强制事务上下文）
     *
     * @param id 主键 ID
     * @return 加锁实体
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    E getForUpdateById(Long id);
}

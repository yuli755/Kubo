package cn.hamm.spms.base;

import cn.hamm.spms.framework.curd.base.ICurdRepository;

/**
 * <h1>数据源接口</h1>
 *
 * <p>2026-09-18 J2：从 Spring Data JPA 的 {@code JpaRepository} 换成 MyBatis-Plus 的
 * {@link ICurdRepository}（= {@code BaseMapper} + 本仓的行锁/保存垫片）。
 * 它由写侧 {@code @MapperScan(markerInterface = ICurdRepository.class)} 注册 ——
 * **不再有 {@code @NoRepositoryBean}**（那是 Spring Data 的注解，留着只会误导）。</p>
 *
 * @param <E> 实体
 * @author Hamm.cn
 */
public interface BaseRepository<E extends BaseEntity<E>> extends ICurdRepository<E> {
}

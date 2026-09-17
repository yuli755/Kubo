package cn.hamm.spms.base.mapper;

import cn.hamm.spms.module.system.datapermission.mapper.DataFieldPolicyQueryMapper;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import cn.hamm.spms.module.wms.inventory.mapper.InventoryWriteMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>无条件注册的 Mapper（逐个显式登记，不跟任何灰度开关）</h1>
 *
 * <p>本仓的读侧 Mapper（继承 {@link KuboBaseMapper}）由 {@code MapperReadConfiguration} 在
 * {@code kubo.data-permission.mapper-read.enabled=true} 时用 {@code @MapperScan} 注册 ——
 * 那个开关是灰度与回滚位，业务读路径必须能"整体不存在"。</p>
 *
 * <p>但下面这些 Mapper <b>不能</b>跟着它消失，所以在这里逐个登记：</p>
 * <ul>
 *   <li>审计/证据日志（4 个）：这些表是"业务失败也要留痕"的旁路，在换成 MyBatis-Plus 之前它们
 *       是 Spring Data JPA 仓储，**一直存在**；换轨必须保住这个性质；</li>
 *   <li>{@code InventoryWriteMapper}：库存原子增减是写路径 —— 读侧开关关掉时读端点整体消失是对的，
 *       但写侧的扣减跟着消失就是"静默不扣库存"；</li>
 *   <li>{@code DataFieldPolicyQueryMapper}：脱敏策略是权限判定链的一部分，关掉开关反而变成"查不到策略
 *       ⇒ 放行"，与开关的本意相反。</li>
 * </ul>
 *
 * <p><b>判断标准</b>：这个 Mapper 的能力缺了会不会"悄悄地少做一件事"？会 → 登记在这里。</p>
 *
 * <p><b>为什么不用再写一个 {@code @MapperScan}</b>：契约测试 `maskContract.spec.ts` 明确钉住了
 * "注册面只有一个"（多于一处 = 开关管不住全部）。这一条判据是对的 —— 与其为了省事扩大注册面，
 * 不如在这里**逐个用 {@code MapperFactoryBean} 显式登记**：读代码的人一眼能看到
 * "哪几个 Mapper 不走开关"，而且这几行本身就是它不受开关影响的证据。</p>
 *
 * <p>Bean 名字沿用接口的驼峰名（Spring 默认按方法名），所以
 * {@code ObjectProvider<DataPermissionDenyLogRepository>} 这类注入方式**一个字都不用改**。</p>
 *
 * @author Hamm.cn
 */
@Configuration(proxyBeanMethods = false)
public class InfraMapperConfiguration {
    /**
     * 登记一个基础设施 Mapper（MyBatis 的 MapperFactoryBean，交给 Spring 管理）
     *
     * @param mapperInterface Mapper 接口
     * @param sqlSessionFactory MyBatis 的会话工厂（由 starter 自动装配）
     * @param <M> Mapper 类型
     * @return MapperFactoryBean
     */
    private static <M> MapperFactoryBean<M> register(Class<M> mapperInterface, SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<M> factoryBean = new MapperFactoryBean<>(mapperInterface);
        factoryBean.setSqlSessionFactory(sqlSessionFactory);
        return factoryBean;
    }

    /**
     * 拒绝事件表（数据权限 OFF/SHADOW 档下的证据）
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<DataPermissionDenyLogRepository> dataPermissionDenyLogRepository(SqlSessionFactory sqlSessionFactory) {
        return register(DataPermissionDenyLogRepository.class, sqlSessionFactory);
    }

    /**
     * 影子比对差异表
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<DataPermissionDiffRepository> dataPermissionDiffRepository(SqlSessionFactory sqlSessionFactory) {
        return register(DataPermissionDiffRepository.class, sqlSessionFactory);
    }

    /**
     * ORM 行数对账表
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<DataPermissionOrmParityRepository> dataPermissionOrmParityRepository(SqlSessionFactory sqlSessionFactory) {
        return register(DataPermissionOrmParityRepository.class, sqlSessionFactory);
    }

    /**
     * 通用操作审计日志表
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<OperationLogRepository> operationLogRepository(SqlSessionFactory sqlSessionFactory) {
        return register(OperationLogRepository.class, sqlSessionFactory);
    }

    /**
     * 库存写侧：原子增减（移库/入库/出库都在走它，绝不能跟着读侧开关消失）
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<InventoryWriteMapper> inventoryWriteMapper(SqlSessionFactory sqlSessionFactory) {
        return register(InventoryWriteMapper.class, sqlSessionFactory);
    }

    /**
     * 字段脱敏策略（带资源 join 的那条查询；策略查不到会被读成"放行"，所以不能受开关影响）
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return Mapper
     */
    @Bean
    public MapperFactoryBean<DataFieldPolicyQueryMapper> dataFieldPolicyQueryMapper(SqlSessionFactory sqlSessionFactory) {
        return register(DataFieldPolicyQueryMapper.class, sqlSessionFactory);
    }
}

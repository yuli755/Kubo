package cn.hamm.spms.base.mapper;

import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDenyLogRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.repository.OperationLogRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>基础设施 Mapper 的注册（无条件、逐个显式）</h1>
 *
 * <p>本仓的读侧 Mapper（继承 {@link KuboBaseMapper}）由 {@code MapperReadConfiguration} 在
 * {@code kubo.data-permission.mapper-read.enabled=true} 时用 {@code @MapperScan} 注册 ——
 * 那个开关是灰度与回滚位，业务读路径必须能"整体不存在"。</p>
 *
 * <p>但审计/证据日志（本类注册的 4 个）不能跟着它消失：这些表是"业务失败也要留痕"的旁路，
 * 在换成 MyBatis-Plus 之前它们是 Spring Data JPA 仓储，**一直存在**；换轨必须保住这个性质。</p>
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
}

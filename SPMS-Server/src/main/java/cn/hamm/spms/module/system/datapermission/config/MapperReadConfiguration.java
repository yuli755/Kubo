package cn.hamm.spms.module.system.datapermission.config;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.system.datapermission.service.DataResourceBindingRegistry;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import cn.hamm.spms.module.system.datapermission.service.RowParityRegistry;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>读侧 Mapper 的注册开关（默认关）</h1>
 *
 * <p>{@code kubo.data-permission.mapper-read.enabled=false}（见 {@code application.yml}）时，
 * <b>容器里没有任何 Mapper Bean</b>：不是"注册了但不用"，是根本没注册。这样"关掉 = 行为与换轨前
 * 逐字节相同"这句话才有得验，回滚也只是改一行配置。</p>
 *
 * <p>为什么开关能做干净：本仓的读侧接口一律不标 {@code @Mapper}（{@link KuboBaseMapper} 里写了理由），
 * MyBatis-Plus 那个只认 {@code @Mapper} 的自动扫描器因此扫不到东西；注册权集中在这里，
 * 用 {@code markerInterface} 认"继承 {@link KuboBaseMapper}"这一条约定，新资源加 Mapper 不必改本类。</p>
 *
 * @author Hamm.cn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kubo.data-permission.mapper-read", name = "enabled", havingValue = "true")
@MapperScan(basePackages = "cn.hamm.spms", markerInterface = KuboBaseMapper.class)
public class MapperReadConfiguration {

    /**
     * 对账探针的索引：从容器里的 {@link RowParityCounter} 汇总，不在这里逐个点名资源
     *
     * <p>为什么住在这里：本类由 {@code mapper-read.enabled} 整块挡住，注册表跟着它在/不在，
     * "开关关掉 ⇒ 一条对账 SQL 都不会发"就自动成立，不需要第二个开关。反过来，如果这张表由
     * 某个总配置类装配，就会出现"探针没注册但 Mapper 已注册"这种半开状态，那种状态比全关难查。</p>
     *
     * <p>用 {@link ObjectProvider#orderedStream()} 而不是 {@code List<RowParityCounter>} 形参：
     * 后者在一个实现都没有时是按"缺 Bean"处理的，会让本类的存在与否取决于"有没有人写第一个 Mapper" ——
     * 那等于把地基的可装配性挂在某个模块身上。流式取就没有这个耦合（空表 = 合法状态）。</p>
     *
     * @param counters 容器里的探针
     * @return 注册表（可能为空）
     */
    @Bean
    RowParityRegistry dataPermissionRowParityRegistry(ObjectProvider<RowParityCounter> counters) {
        return new RowParityRegistry(counters.orderedStream().toList());
    }

    /**
     * 启动期把 MyBatis 的 statement ID 展开成数据资源绑定。
     *
     * <p>它与 {@link RowParityRegistry} 同挂在本配置块上：开关关着时既没有读 Mapper，
     * 也没有一张看似存在但无人维护的绑定表；开关打开后缺 {@code @DataResource} 或缺 XML
     * statement 直接启动失败，后续权限拦截器不需要在请求线程猜资源。</p>
     *
     * @param sqlSessionFactory 已完成 Mapper/XML 装配的工厂
     * @return statement 到资源的绑定表
     */
    @Bean
    DataResourceBindingRegistry dataResourceBindingRegistry(SqlSessionFactory sqlSessionFactory) {
        return DataResourceBindingRegistry.from(sqlSessionFactory);
    }
}

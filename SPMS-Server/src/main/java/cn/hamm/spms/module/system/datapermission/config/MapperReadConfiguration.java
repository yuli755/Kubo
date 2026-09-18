package cn.hamm.spms.module.system.datapermission.config;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.system.datapermission.mybatis.KuboDataPermissionHandler;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceBindingRegistry;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
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
 * <p><b>2026-09-18 J4</b>：本类先前还提供 {@code RowParityRegistry}（"哪个资源有 Mapper 侧探针"的索引）
 * 与 {@code RowParityCounter} 一起支撑"JPA 侧 vs Mapper 侧"的行数对账 —— 那套对账随
 * {@code spring-boot-starter-data-jpa} 一起退场（只剩一条渲染/读取路径，没有第二种 ORM 可比），
 * 本类现在只做两件事：按开关注册读侧 Mapper、把 statement 展开成资源绑定。</p>
 *
 * @author Hamm.cn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kubo.data-permission.mapper-read", name = "enabled", havingValue = "true")
@MapperScan(basePackages = "cn.hamm.spms", markerInterface = KuboBaseMapper.class)
public class MapperReadConfiguration {

    /**
     * statement 到资源的运行时权限决策器。它与读侧 Mapper 同挂在一个开关下，避免出现
     * "Mapper 已注册但权限插件没有注册"的半开状态。
     */
    @Bean
    KuboDataPermissionHandler kuboDataPermissionHandler(
            ObjectProvider<DataResourceBindingRegistry> bindings,
            DataPermissionService dataPermissionService,
            cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties properties,
            DataPermissionAudit audit,
            PermissionRuleTreeBuilder ruleTreeBuilder,
            SqlPermissionRuleRenderer renderer,
            cn.hamm.spms.module.system.datapermission.service.ShadowDiffService shadowDiffService) {
        return new KuboDataPermissionHandler(bindings::getObject, dataPermissionService, properties, audit,
                ruleTreeBuilder, renderer, shadowDiffService);
    }

    // 2026-09-18 J2：MybatisPlusInterceptor 这个 Bean 搬去了 CurdMapperConfiguration
    //（MyBatis-Plus 只认一个插件 Bean，写侧分页插件必须永远注册）。本类继续提供
    // KuboDataPermissionHandler（受开关控制），由那边的 ObjectProvider 条件性挂上
    // DataPermissionInterceptor —— “开关关掉 ⇒ 容器里没有这条读路径”的口径没变。

    /**
     * 启动期把 MyBatis 的 statement ID 展开成数据资源绑定。
     *
     * <p>它与读侧 Mapper 同挂在本配置块上：开关关着时既没有读 Mapper，
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

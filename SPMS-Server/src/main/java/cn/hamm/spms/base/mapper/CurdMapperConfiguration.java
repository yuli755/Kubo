package cn.hamm.spms.base.mapper;

import cn.hamm.spms.framework.curd.base.ICurdRepository;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.mybatis.KuboDataPermissionHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <h1>写侧 Mapper 注册与 MyBatis-Plus 插件（J2）</h1>
 *
 * <p>本类是全仓**第二个** {@code @MapperScan}（第一个在读侧 {@code MapperReadConfiguration}，
 * 挂 {@code kubo.data-permission.mapper-read} 开关）：</p>
 *
 * <ul>
 *   <li><b>写侧不挂任何开关</b>：增删改不能因为"读侧灰度关掉"就整体消失 —— 那不是灰度，是停机；</li>
 *   <li>marker 是 {@link ICurdRepository}：只有写侧仓储会被注册，读侧 Mapper（{@code KuboBaseMapper} 族）
 *       仍归读侧那处扫；</li>
 *   <li>扫描根写 {@code cn.hamm.spms.module} 而不是整个 {@code cn.hamm.spms}：框架层的
 *       {@code BaseRepository}/{@code BaseBillRepository}/{@code BaseBillDetailRepository} 是带类型变量的
 *       抽象接口，注册进去 MP 会拿 {@code E} 去建表信息并在启动时炸；扫描根只覆盖业务域，
 *       让"抽象基类不注册"成为结构性事实而不是一条约定。新增写侧仓储请放在 {@code module/} 下。</li>
 * </ul>
 *
 * <p>插件也在这里装配，因为 <b>MyBatis-Plus 只认一个 {@code MybatisPlusInterceptor} Bean</b>：</p>
 *
 * <ul>
 *   <li>{@link PaginationInnerInterceptor}：**永远注册**。不注册的后果是静默的 ——
 *       {@code selectPage} 不分页（全量返回）且 {@code total} 恒 0，接口看着"能跑"；</li>
 *   <li>{@link DataPermissionInterceptor}：只在读侧开关打开、且读侧 handler Bean 在场时追加，
 *       保持"开关关掉 ⇒ 容器里没有这条读路径"这一条口径不变。</li>
 * </ul>
 *
 * @author Arena agent
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "cn.hamm.spms.module", markerInterface = ICurdRepository.class)
public class CurdMapperConfiguration {
    /**
     * 唯一的 MyBatis-Plus 插件入口
     *
     * @param handlerProvider 读侧数据权限 handler（受 mapper-read 开关控制，可能不存在）
     * @param properties      数据权限配置（保留参数：拦截器注册顺序与开关相关）
     * @return 插件
     */
    @Bean
    MybatisPlusInterceptor kuboMybatisPlusInterceptor(
            ObjectProvider<KuboDataPermissionHandler> handlerProvider,
            DataPermissionProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        handlerProvider.ifAvailable(handler -> interceptor.addInnerInterceptor(new DataPermissionInterceptor(handler)));
        return interceptor;
    }
}

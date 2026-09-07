package cn.hamm.spms.module.system.datapermission.config;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
}

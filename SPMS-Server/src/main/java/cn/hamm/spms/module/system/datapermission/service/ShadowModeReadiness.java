package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * <h1>影子线的启动守卫（J3：把"静默降级"变成"启动失败"）</h1>
 *
 * <p>影子对账靠的是"JPA 侧算一遍、MyBatis 侧算一遍，比较行数"。J3 把实体上的 JPA 注解全部去掉了
 * （见 docs《去JPA决策评估》§9.7），Hibernate 的实体集合因此为空 —— 那条 JPA 路径<b>已经没有实体可查</b>。
 * 本批不去重写它（那是 J4：随 {@code spring-boot-starter-data-jpa} 一起退场），但也不能让它在运行期
 * 一行行打 {@code shadow-compare-failed} 日志：那种日志读起来像"偶发查询失败"，
 * 而真相是"这条线整体不可用"，两者的处置方式完全不同。</p>
 *
 * <p>所以判据放到启动期：<b>只要数据权限不在 {@code off}（或读侧 Mapper 开着），
 * 就要求 JPA 实体集合非空</b>；不满足直接抛异常并指到文档。{@code application.yml} 当前是
 * {@code mode: off}（默认态），因此本批交付不影响正常启动；谁要打开 shadow/enforce，
 * 得到的是"为什么打不开、下一步做什么"，而不是一个看起来在跑的对账。</p>
 *
 * @author Arena agent
 */
@Service
public class ShadowModeReadiness {
    private static final Logger log = LoggerFactory.getLogger(ShadowModeReadiness.class);

    private final DataPermissionProperties properties;
    private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

    /**
     * 读侧 Mapper 开关（{@code kubo.data-permission.mapper-read.enabled}）：
     * 它打开时影子对账的第三个 COUNT 才存在，因此同样受本守卫约束
     */
    @Value("${kubo.data-permission.mapper-read.enabled:false}")
    private boolean mapperReadEnabled;

    public ShadowModeReadiness(DataPermissionProperties properties,
                               ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider) {
        this.properties = properties;
        this.entityManagerFactoryProvider = entityManagerFactoryProvider;
    }

    /**
     * 启动期核对：影子线要用到的 JPA 实体集合必须还在
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        if (Objects.isNull(properties)) {
            return;
        }
        boolean shadowNeeded = Objects.nonNull(properties.getMode())
                && properties.getMode() != DataPermissionProperties.Mode.OFF;
        if (!shadowNeeded && !mapperReadEnabled) {
            // 默认态（off + mapper-read 关）：影子线本来就不参与，零成本零噪音
            return;
        }
        EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.getIfAvailable();
        if (Objects.isNull(entityManagerFactory)) {
            // J4 之后连 starter 都不在：影子线已经整体退场，这里没有可校验的对象
            log.info("[data-permission][shadow] 容器里没有 EntityManagerFactory：影子线不在（J4 之后为常态）");
            return;
        }
        int entityCount = entityManagerFactory.getMetamodel().getEntities().size();
        if (entityCount > 0) {
            return;
        }
        throw new IllegalStateException(
                "数据权限影子线不可用：JPA 实体集合为空（J3 已把 41 个实体上的 @Entity 换成 @TableName）。\n"
                        + "  受影响：ShadowDiffService / JpaRowVisibilityProbe / 14 个 *ReadShadowService / "
                        + "PermissionRuleSupport 的 JPA 渲染路径。\n"
                        + "  处置：把 kubo.data-permission.mode 置回 off（当前默认态），"
                        + "或按 docs《去JPA决策评估》§9.7.4 在 J4 把影子线换成 MyBatis-Plus 渲染后再打开。\n"
                        + "  为什么不降级运行：那会让对账每个资源都报 diff-probe-saturated / shadow-compare-failed，"
                        + "看起来像偶发故障，实际是整条线不可用。");
    }
}

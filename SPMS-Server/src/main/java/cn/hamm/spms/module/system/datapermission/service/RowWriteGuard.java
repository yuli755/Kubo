package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseEntity;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.base.spi.RowPermissionEnforcer;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <h1>写侧行级守卫：这一行到底能不能改 / 能不能删</h1>
 *
 * <p>{@link DataPermissionGuard} 回答的是"<b>这个动作</b>配了策略没有"，它不看行 —— 于是"仓库管理员
 * 只能动本仓的单据"这类规则在写侧其实是空的：只要资源上有一条 UPDATE 策略，任何单据都能改。
 * 读侧被过滤掉的行，到了写侧却能改，这是权限模型里最难被察觉的不对称。</p>
 *
 * <p>本类的判据刻意<b>不新造一套</b>：把这条记录交给读侧同一个条件生成器
 * （{@link DataPermissionSpecification#applyQuery}）跑一次 COUNT，"看得见"才"改得动"。
 * 只有这样才能保证读写一致 —— 否则会出现"列表里没有、但保存成功"或反过来。</p>
 *
 * <h2>为什么默认关</h2>
 *
 * <p>写侧失败的代价与读侧不同：读侧绑不上是"查不到数据"（可容忍、看得见），写侧绑不上是
 * "<b>保存被拒</b>"，直接打断业务。而本模块盘点出来的现实是不少资源的维度列压根绑不上
 * （见 §19.25 / §19.26），条件在那类资源上是恒假谓词 ⇒ 一开就是全员改不了单。
 * 所以 {@code kubo.data-permission.row-write.enabled} 默认 false，
 * 灰度顺序固定为：<b>先把「维度绑定风险」面板清零 → 开 shadow 看 deny 指标 → 再进 enforce 名单</b>。</p>
 *
 * <h2>边界</h2>
 * <ul>
 *   <li>CREATE 不校验：新记录没有既存行可判；"归属必须落谁"是 B1 的建模问题，不在这里偷做。</li>
 *   <li>无上下文 / bypass（ROOT、定时任务）放行，与 {@link DataPermissionGuard} 同口径。</li>
 *   <li>条件生成异常按<b>不可见</b>处理（fail-closed），但 shadow 模式下只记审计不拒 ——
 *       与读侧"enforce 拒、shadow 记账"完全一致。</li>
 *   <li>校验的是<b>库里既存行</b>（探针把 flush 设为 {@code COMMIT}，即查询前不 flush），不是"改完之后"：
 *       否则把 {@code creatorId} 改成别人就能让自己从视野里消失再随便改，等于给绕过留了门。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class RowWriteGuard implements RowPermissionEnforcer {

    /**
     * 既存行可见性探针（实现在 {@link RowVisibilityProbe}：与读侧同一份条件、同一套 wrapper）
     *
     * <p>单独抽出来的理由与本模块所有判定一致：把"拿数据"与"下结论"分开，
     * 结论这一半必须能在不起 Spring、不连库的情况下被逐条断言。</p>
     */
    public interface VisibilityProbe {

        /**
         * 该主体在指定动作下能否看见这一行
         *
         * @param resourceKey 资源标识
         * @param entityClass 实体类
         * @param action      写入动作
         * @param id          记录主键
         * @return 可见返回 true
         * @throws RuntimeException 条件生成失败（由调用方按不可见处理）
         */
        boolean visible(String resourceKey, Class<?> entityClass, DataPermissionAction action, Long id);
    }

    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final VisibilityProbe probe;

    public RowWriteGuard(DataPermissionProperties properties, DataPermissionAudit audit, VisibilityProbe probe) {
        this.properties = properties;
        this.audit = audit;
        this.probe = probe;
    }

    /**
     * 校验当前主体能否写入这条既存记录
     *
     * @param entity      待写入实体（取其类与主键）
     * @param resourceKey 资源标识；{@code null} 表示该服务未登记数据资源 ⇒ 不参与
     * @param action      UPDATE / DELETE（CREATE 直接放行，见类注释）
     */
    @Override
    public void enforce(BaseEntity<?> entity, String resourceKey, DataPermissionAction action) {
        if (entity == null || resourceKey == null) {
            return;
        }
        if (action == DataPermissionAction.CREATE || action == DataPermissionAction.QUERY
                || action == DataPermissionAction.EXPORT) {
            return;
        }
        if (!properties.getRowWrite().isEnabled() || !properties.computes(resourceKey)) {
            // 开关关闭时连探针都不问：这一档是"额外一次 COUNT"，默认关就不该产生任何成本
            return;
        }
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return;
        }

        boolean visible;
        String reason = DataPermissionAudit.REASON_ROW_NOT_WRITABLE;
        try {
            visible = probe.visible(resourceKey, entity.getClass(), action, id);
        }
        catch (RuntimeException ex) {
            // 条件生成失败绝不能当成"可见"：那等于探针坏了就自动放行
            visible = false;
            reason = DataPermissionAudit.REASON_PARSE_FAILED;
            log.debug("[data-permission][row-write] 条件生成失败 resource={} action={}: {}",
                    resourceKey, action, ex.getMessage());
        }
        if (visible) {
            return;
        }

        String detail = "id=" + id + " action=" + action.name() + " user=" + context.userId();
        if (!properties.enforced(resourceKey)) {
            audit.shadow(resourceKey, reason, "写侧行级本应拒绝（" + detail + "）");
            return;
        }
        DataPermissionDenial.denied(audit, resourceKey, reason, detail,
                "数据权限不足：这条记录不在你当前的数据范围内（" + resourceKey + " " + action.name()
                        + "，ID " + id + "）。若确认它归你，请到资源页检查归属列登记；"
                        + "列表刷新后仍在，说明该行属于别人的数据。");
    }
}

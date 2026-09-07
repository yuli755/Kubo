package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * <h1>数据权限写入守卫</h1>
 *
 * <p>在新增/修改/删除等写入操作中执行数据权限白名单校验：
 * 仅当当前主体与资源之间存在 {@code CREATE}/{@code UPDATE}/{@code DELETE} 动作的
 * 数据权限策略时才放行，否则拒绝写入。</p>
 *
 * <p>对 {@link PermissionContext#bypassDataPermission()} 的用户（ROOT 等）天然放行。</p>
 *
 * <p><b>三处短路，顺序固定</b>：无上下文 / bypass ⇒ 直接返回；{@code mode=off} 或该资源被降回 off
 * ⇒ 直接返回（回滚档必须真的不参与，见 {@link DataPermissionProperties#computes(String)}）；
 * 资源未登记 / 未启用 ⇒ 按 {@code deny-unregistered} 决定拒还是放，与读侧同一规则。
 * 之后才问"这个动作有没有策略"。</p>
 *
 * <p>这里抛的是 {@link IllegalStateException}，全局异常处理会映射成 500 而不是 403 ——
 * 与 {@code DataPermissionSpecification} 的既有口径一致，统一成 403 属于 B5 的错误语义收口，
 * 不在本刀范围（改了会影响所有依赖 500 的前端提示分支，需要一次单独联调）。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionGuard {

    private final DataPermissionService dataPermissionService;

    private final DataPermissionProperties properties;

    private final DataPermissionAudit audit;

    public DataPermissionGuard(DataPermissionService dataPermissionService,
                               DataPermissionProperties properties,
                               DataPermissionAudit audit) {
        this.dataPermissionService = dataPermissionService;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * 校验当前请求主体是否拥有指定资源、指定写入动作的数据权限
     *
     * @param resourceKey 数据资源标识（如 {@code wms.input}）
     * @param action      写入动作（CREATE / UPDATE / DELETE）
     */
    public void enforce(String resourceKey, DataPermissionAction action) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            // 无权限上下文（如系统任务），不做拦截，保持向后兼容
            return;
        }
        if (context.bypassDataPermission()) {
            return;
        }
        if (!properties.computes(resourceKey)) {
            // mode=off（或该资源被降回 off）时这里<b>必须什么都不做</b>。
            // 本模块对外的承诺是「OFF 档行为等价于改造前」，而回滚手段就是把这个配置改回去 ——
            // 写侧不看 mode，等于"回滚"这句话在保存路径上是假的（读侧早就看了）。
            // 这一格之前就是无条件抛，所以每次有人改 data_resource 表之外的东西都可能撞到。
            return;
        }

        if (dataPermissionService.findResource(resourceKey) == null) {
            // 未登记 / 未启用：与读侧同一个三态判据（deny-unregistered），而不是"必然 500"。
            // 现状是没登记就抛 IllegalStateException，于是「新模块刚加进来、还没在 data_resource 里建行」
            // 与「资源键被改名」都表现为所有保存/删除 500，而配置页上看不出任何原因。
            if (properties.isDenyUnregistered()) {
                audit.deny(resourceKey, DataPermissionAudit.REASON_UNREGISTERED,
                        "写侧 " + action.name() + "：资源未登记或未启用，deny-unregistered=true");
                throw new IllegalStateException(
                        "数据权限：资源 " + resourceKey + " 尚未登记或未启用，而当前配置要求未登记一律拒绝"
                                + "（kubo.data-permission.deny-unregistered=true）。"
                                + "请到「数据资源」把该资源登记并启用，或由管理员确认此项配置。");
            }
            // 放开也要留计数：否则"哪些服务还没纳管"这件事在指标里完全不存在
            audit.shadow(resourceKey, DataPermissionAudit.REASON_UNREGISTERED,
                    "写侧 " + action.name() + "：资源未登记，按 deny-unregistered=false 放行");
            return;
        }

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey, action, context);
        if (policies.isEmpty()) {
            audit.deny(resourceKey, DataPermissionAudit.REASON_WRITE_DENIED,
                    "写侧 " + action.name() + "：该主体在此动作下没有任何策略");
            throw new IllegalStateException(
                    "数据权限不足：当前用户无 " + resourceKey + " 的 " + action.name() + " 权限。"
                            + "若刚把该资源加进 enforce 名单，说明只配了查询侧策略 —— "
                            "请到「数据策略」为更新 / 删除动作补策略，或先把该资源移出名单。");
        }
    }
}
package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;

import java.util.Set;

/**
 * <h1>探针查询的旁路上下文（J4）</h1>
 *
 * <p>影子比对与写侧可见性探针都走<b>实体仓储</b>，也就是都经过 MyBatis-Plus 的插件链。
 * 这两条查询的 WHERE 已经由调用方自己拼好（{@link DataPermissionSpecification#applyQuery}
 * 或渲染出的片段），此时如果权限拦截器再注入一层条件，数出来的就不是"我们要数的那个查询"：</p>
 * <ul>
 *   <li>在 {@code kubo.data-permission.mapper-read.enabled=true} 且全局 enforce 的配置下，
 *       仓储语句（{@code ICurdRepository.selectCount}）不在 {@code @DataResource} 绑定表里，
 *       拦截器按 fail-closed 给未绑定 statement 挂恒定假条件 —— 探针会数到 0；</li>
 *   <li>即使没到恒假那一步，拦截器路径还会写 binding 指标，把"探针自己的查询"算成一次绑定失败，
 *       污染"放开 enforce 前 binding 必须清零"这个前置。</li>
 * </ul>
 *
 * <p>所以探针在<b>数数的那一瞬间</b>显式声明旁路：条件由探针自己保证完整，拦截器不必也不许再加。
 * 这不放宽任何判定 —— 放行/拒绝的结论仍然来自 {@link RowWriteGuard} 与渲染器，探针只回答"数到几行"。</p>
 *
 * <p>身份取 0（系统）：{@link PermissionContext} 的构造器要求非空用户/租户，而旁路上下文不参与任何
 * 策略求值，只用于让拦截器短路。真实身份（谁发起了这次查询）由调用方另行记进差异表。</p>
 *
 * @author Hamm.cn
 * @see ShadowDiffService
 * @see RowVisibilityProbe
 */
final class DataPermissionProbeContext {

    /**
     * 旁路上下文里的"系统"身份（不参与策略求值，只为满足上下文的非空约束）
     */
    private static final long SYSTEM_ID = 0L;

    private DataPermissionProbeContext() {
    }

    /**
     * 打开旁路上下文，用完 close（{@link PermissionContextHolder.Scope} 会恢复上一层上下文）
     *
     * @return 作用域；放在 try-with-resources 里
     */
    static PermissionContextHolder.Scope open() {
        return PermissionContextHolder.open(new PermissionContext(
                SYSTEM_ID, SYSTEM_ID, Set.of(), Set.of(), Set.of(), Set.of(), true));
    }
}

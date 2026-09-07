package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN;

/**
 * <h1>数据权限的拒答出口（唯一一份）</h1>
 *
 * <p>"被数据权限挡了"这件事在本模块里有四个入口会说出口：{@link DataPermissionGuard}（动作级写侧）、
 * {@link RowWriteGuard}（行级写侧）、{@link ExportBypassGuard}（导出旁路）、以及读侧的恒假条件。
 * 前三处以前各写各的：<em>导出走 {@code FORBIDDEN.show}，写侧与行级走 {@code throw new
 * IllegalStateException}</em> —— 同一个判定结果在两条路径上状态码不同（403 与 500），于是前端的提示、
 * 网关的告警、值班的第一反应全都不一样，而"为什么被拒"的文案也是各写一份、各自漂移。
 * 更隐蔽的是留痕：导出那条只记了 shadow，<b>真拒了不进 {@code kubo.datapermission.deny} 计数</b>，
 * 放开后想知道"挡了多少人导出"只能去翻日志。</p>
 *
 * <p>这个类只做一件事：<b>先留痕，再以「权限不足」的语义抛出</b>。顺序是有意的 —— 反过来的话，
 * 一旦文案拼装里某句抛了（拼接的资源键为 null 之类），这次拒绝就在指标里彻底消失。</p>
 *
 * <h2>分类纪律（什么该走这里，什么不该）</h2>
 * <ul>
 *   <li><b>权限判定结果</b>（这个人、在这个档位、对这个资源/这一行没有权限）→ 走这里，语义是 403；</li>
 *   <li><b>配置与接线错误</b>（资源没登记导致的校验失败、反射读不到字段、实体无法复制、启动期 {@code @SensitiveField}
 *       标在基本类型上）→ <b>不</b>走这里，仍然 {@code IllegalStateException} / 500。
 *       把它们伪装成"你没权限"，运维会去查授权表而真正的 bug 在代码里 —— 这类错必须由状态码本身指路。</li>
 * </ul>
 *
 * <p>刻意做成静态方法而不是 Bean：调用方的审计组件在测试里是 mock，<b>把抛异常放进被 mock 的对象里，
 * 测试就再也不抛了</b>（本仓被这类"替身恰好盖住出问题那条分支"红过两次）。静态出口 + 显式把 audit 当参数传进来，
 * mock 只负责"有没有留痕"，抛与不抛由这段真代码决定。</p>
 *
 * @see DataPermissionProperties.Mode 档位语义
 * @see DataPermissionAudit#deny 留痕口径
 */
public final class DataPermissionDenial {

    private DataPermissionDenial() {
        // 工具类
    }

    /**
     * 记一次 enforce 拒绝并抛出「权限不足」
     *
     * @param audit       审计组件；为 null 时<em>照样拒</em>（打点坏了不能变成放行）
     * @param resourceKey 资源标识
     * @param reason      原因串（取 {@code DataPermissionAudit.REASON_*} 或 {@code ExportBypassGuard.REASON_*}）
     * @param detail      给运维看的明细（ID、字段名、动作等）
     * @param message     给用户看的一句话，必须含"数据权限不足"与下一步动作
     */
    public static void denied(DataPermissionAudit audit,
                              String resourceKey,
                              String reason,
                              String detail,
                              String message) {
        if (audit != null) {
            audit.deny(resourceKey, reason, detail);
        }
        FORBIDDEN.show(message);
        // 仓内既有注释确认 FORBIDDEN.show 必然抛（见 TenantContextResolver#resolve）。这里再兜一层：
        // 万一框架把它改成"只登记不抛"，宁可回落到 500，也绝不能悄悄放行 —— 失败只收紧不放开。
        throw new IllegalStateException(message);
    }
}

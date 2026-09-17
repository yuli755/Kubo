package cn.hamm.spms.base.config;

import cn.hamm.spms.core.interfaces.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>系统配置类型</h1>
 *
 * <p><b>2026-09-18 分层搬迁</b>：本类型原在 {@code cn.hamm.spms.module.system.config.enums}，由<b>业务域</b>搬到
 * {@code base/config} —— 它是纯字典值类型（只依赖 {@code core} 与 JDK），
 * 而"用到它的最下层"是 {@code base/bill/AbstractBaseBillService} 的
 * {@code getAutoAuditConfigFlag()} 扩展点。留在业务域就是"下层依赖上层"。
 * 存储、缓存与保存期校验（{@code ConfigService} / {@code ConfigEntity}）
 * 仍然留在 {@code module.system.config}。</p>
 *
 * @author Hamm.cn
 */
@Getter
@AllArgsConstructor
public enum ConfigType implements IDictionary {
    /**
     * 字符串类型
     */
    STRING(0, "字符串类型"),

    /**
     * 布尔类型
     */
    BOOLEAN(1, "布尔类型"),

    /**
     * 数字类型
     */
    NUMBER(2, "数字类型"),
    ;

    private final int key;
    private final String label;
}

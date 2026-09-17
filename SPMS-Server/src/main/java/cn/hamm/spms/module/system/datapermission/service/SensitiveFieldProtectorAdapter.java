package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.spi.SensitiveFieldProtector;
import cn.hamm.spms.framework.curd.datapermission.MaskedInputRegistry;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

/**
 * <h1>受限字段写保护的 SPI 适配器</h1>
 *
 * <p><b>为什么要这个类</b>：{@link SensitiveWriteProtection} 是本能力的唯一真相（静态工具 + 单测），
 * 它不能变成可被替换的 Bean —— 本仓吃过这类亏：替身把守卫喂绿，生产里保护其实没接上。
 * 所以这里只做转发，判定与恢复仍然只有 {@code SensitiveWriteProtection} 一处实现。</p>
 *
 * <p><b>必须是常驻 Bean</b>：本类不许加 {@code @Profile} 之类的条件装配。
 * 它一旦缺席，{@code base/BaseService} 侧就是"钩子还在、保护没了"，
 * 而后果是把掩码串写进真实列（不可逆）。契约测试会读文件确认没有条件装配。</p>
 *
 * <p><b>"要不要回表"落在这一层</b>：base 只把取数动作作为 {@link Supplier} 交进来，
 * 由这里决定何时调用 —— 两条识别路径都不命中时一次库都不查，正常保存仍是一次 UPDATE。</p>
 *
 * @author Hamm.cn
 */
@Component
public class SensitiveFieldProtectorAdapter implements SensitiveFieldProtector {
    @Override
    public void rejectMaskedOnAdd(Object incoming) {
        SensitiveWriteProtection.rejectMaskedOnAdd(incoming);
    }

    @Override
    public boolean restoreOnUpdate(Object incoming, PermissionContext context, Supplier<Object> storedLoader) {
        // 两条互补的识别路径（顺序不能反：登记命中时连策略都不用查）：
        //   1) 登记命中 —— 反序列化阶段认出"像掩码"的占位值（星号口径，零成本，无需回表）；
        //   2) 输出复算 —— 拿库里原值套用同一份生效策略算出"这个用户本该看到的串"再比对。
        Class<?> type = incoming.getClass();
        boolean fromRegistry = SensitiveWriteProtection.guards(type)
                && MaskedInputRegistry.hasAny()
                && SensitiveWriteProtection.hasMaskedInput(type);
        ColumnMaskingService maskingService = DataPermissionRuntime.maskingService();
        boolean fromOutputMatch = SensitiveWriteProtection.mayContainMaskedOutput(type, maskingService, context);
        if (!fromRegistry && !fromOutputMatch) {
            return false;
        }
        Object stored = storedLoader.get();
        if (stored == null) {
            if (fromRegistry) {
                // 取不到原值就没法恢复。此时放行等于把 null（或掩码串）写进该列，比报错糟得多，
                // 所以宁可失败：这条记录的敏感字段本来就只对有权角色可见
                PARAM_INVALID.show("记录不存在或无权访问，敏感字段的脱敏值无法安全处理，请刷新后重试");
            }
            return false;
        }
        boolean restored = SensitiveWriteProtection.restoreOnUpdate(incoming, stored);
        restored |= SensitiveWriteProtection.restoreMatchingMaskedOutput(incoming, stored, maskingService, context);
        // 消费完即清，避免非 HTTP 入口（异步任务、测试）残留到下一次调用
        MaskedInputRegistry.clear();
        return restored;
    }
}

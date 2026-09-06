package cn.hamm.spms.base;

import cn.hamm.airpower.curd.base.CurdService;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionRuntime;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionResourceResolver;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.module.system.datapermission.service.MaskedInputRegistry;
import cn.hamm.spms.module.system.datapermission.service.SensitiveWriteProtection;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>基础服务类</h1>
 *
 * @param <E> 实体
 * @param <R> 数据源
 * @author Hamm.cn
 */
@Slf4j
public class BaseService<
        E extends BaseEntity<E>,
        R extends BaseRepository<E>
        > extends CurdService<E, R> {

    /**
     * 当前服务的数据库最后一次确认
     *
     * @param entity 实体
     * @return 处理后的数据
     */
    protected E beforeAppSaveToDatabase(@NotNull E entity) {
        return entity;
    }

    @Override
    protected final @NotNull E beforeSaveToDatabase(@NotNull E entity) {
        CodeRuleService codeRuleService = SystemServices.getCodeRuleService();
        codeRuleService.fillFieldAutoCode(entity);
        return beforeAppSaveToDatabase(entity);
    }

    @Override
    protected E beforeAdd(@NotNull E entity) {
        // 敏感字段被提交成掩码时，新增没有原值可恢复 -> 明确拒绝（否则就是把 **** 建进库）
        SensitiveWriteProtection.rejectMaskedOnAdd(entity);
        E result = super.beforeAdd(entity);
        enforceDataPermission(DataPermissionAction.CREATE);
        return result;
    }

    @Override
    protected E beforeUpdate(@NotNull E entity) {
        E result = super.beforeUpdate(entity);
        protectSensitiveFields(result);
        enforceDataPermission(DataPermissionAction.UPDATE);
        return result;
    }

    /**
     * 受限字段的写侧兜底：提交值是脱敏占位时，用库里的原值覆盖回去
     *
     * <p>为什么放在 {@code BaseService} 而不是各业务 service：漏接一个实体的后果是
     * 把 {@code 138****000} 永久写进真实列。判定与恢复都在
     * {@link SensitiveWriteProtection}，这里只负责"回表取原值"这一次额外查询，
     * 且仅在<b>本次请求确实收到过掩码</b>时才查（正常保存零开销）。</p>
     *
     * <p>{@code beforeUpdate} 在框架语义下可能收到"已合并"的实体，也可能收到"仅提交值"，
     * 本实现两种都成立：只替换被登记为掩码的字段，其余字段原样保留。</p>
     *
     * @param entity 待更新实体
     */
    private void protectSensitiveFields(@NotNull E entity) {
        // 两条互补的识别路径：
        //   1) 登记命中 —— 反序列化阶段认出"像掩码"的占位值（星号口径，零成本，无需回表）；
        //   2) 输出复算 —— 拿库里原值套用同一份生效策略算出"这个用户本该看到的串"再比对，
        //      覆盖 maskExpression 配成非星号文本（如"已隐藏"）的情况，代价是多一次按主键取数。
        // 顺序不能反：登记命中时连策略都不用查就能直接恢复。
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        boolean fromRegistry = SensitiveWriteProtection.guards(entity.getClass())
                && MaskedInputRegistry.hasAny()
                && SensitiveWriteProtection.hasMaskedInput(entity.getClass());
        ColumnMaskingService maskingService = DataPermissionRuntime.maskingService();
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        boolean fromOutputMatch = SensitiveWriteProtection.mayContainMaskedOutput(
                entity.getClass(), maskingService, context);
        if (!fromRegistry && !fromOutputMatch) {
            return;
        }
        E stored = repository.findById(id).orElse(null);
        if (stored == null) {
            if (fromRegistry) {
                // 取不到原值就没法恢复。此时放行等于把 null 写进该列（比报错糟得多），
                // 所以宁可失败：这条记录的敏感字段本来就只对有权角色可见
                PARAM_INVALID.show("记录不存在或无权访问，敏感字段的脱敏值无法安全处理，请刷新后重试");
            }
            return;
        }
        SensitiveWriteProtection.restoreOnUpdate(entity, stored);
        SensitiveWriteProtection.restoreMatchingMaskedOutput(entity, stored, maskingService, context);
        // 消费完即清，避免非 HTTP 入口（异步任务、测试）残留到下一次调用
        MaskedInputRegistry.clear();
    }

    @Override
    protected void beforeDelete(@NotNull E entity) {
        super.beforeDelete(entity);
        enforceDataPermission(DataPermissionAction.DELETE);
    }

    /**
     * 数据权限写入守卫：校验当前主体是否拥有该资源对应写入动作的权限
     *
     * @param action 写入动作
     */
    protected void enforceDataPermission(@NotNull DataPermissionAction action) {
        DataPermissionGuard dataPermissionGuard = SystemServices.getDataPermissionGuard();
        if (dataPermissionGuard == null) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        dataPermissionGuard.enforce(resourceKey, action);
    }

    /**
     * 发布
     *
     * @param id ID
     */
    public final void publish(long id) {
        E entity = get(id);
        beforePublish(entity);
        updateToDatabase(getEntityInstance(id).setIsPublished(true));
    }

    /**
     * 发布前
     *
     * @param entity 实体
     */
    protected void beforePublish(@NotNull E entity) {
        log.info("单据发布前，ID:{}", entity.getId());
    }

    @Override
    protected E afterGet(@NotNull E entity) {
        return afterAppGet(entity);
    }

    protected E afterAppGet(@NotNull E entity) {
        return entity;
    }

    @Override
    protected final void afterAdd(long id, @NotNull E source) {
        afterAppAdd(id, source);
    }

    protected void afterAppAdd(long id, @NotNull E source) {
    }

    @Override
    protected void afterUpdate(long id, @NotNull E source) {
        afterAppUpdate(id, source);
    }

    protected void afterAppUpdate(long id, @NotNull E source) {
    }

    /**
     * 导出内容遮蔽（数据权限）
     *
     * <p>框架的导出行是"反射取字段值 → 拼 CSV"（{@code CollectionUtil.getCsvValueList} →
     * {@code ExportHelper.saveCsvListToFile}，全部静态），不经过 Jackson，
     * 所以序列化层那套 {@code @SensitiveField} 遮罩<b>对导出文件无效</b>。
     * {@code afterExportQuery} 是 {@code CurdService} 上唯一还能插手这批行的位置
     * （{@code createExportTask} 是 final、{@code queryPageToSaveExportFile} 是 private —— 均由 javap 确认）。</p>
     *
     * <p>语义见 {@link ExportColumnMasker}：只改副本、按 QUERY 口径生成掩码、拿到 EXPORT 明文授权的列保留原值。</p>
     */
    @Override
    protected List<E> afterExportQuery(List<E> entities) {
        return ExportColumnMasker.maskForExport(entities);
    }
}

package cn.hamm.spms.base;

import cn.hamm.spms.framework.curd.base.CurdService;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.framework.curd.audit.OperationLog;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.base.permission.AttributionWriteProtection;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionRuntime;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionResourceResolver;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import cn.hamm.spms.framework.curd.datapermission.MaskedInputRegistry;
import cn.hamm.spms.module.system.datapermission.service.OperationLogRecorder;
import cn.hamm.spms.module.system.datapermission.service.RowWriteGuard;
import cn.hamm.spms.module.system.datapermission.service.SensitiveWriteProtection;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

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
     * 通用操作审计记录器（P3）。可选注入：容器未装配该 Bean 时，审计静默跳过，不影响任何业务路径。
     */
    @Autowired(required = false)
    private OperationLogRecorder operationLogRecorder;

    /**
     * 更新前的「旧值」暂存：{@code beforeUpdate} 回表取到的既存行先存这里，
     * {@code afterUpdate} 再取出来连同提交值一起记账。每个请求线程一次更新一条，单槽足够。
     */
    private final ThreadLocal<E> operationLogBeforeSnapshot = new ThreadLocal<>();

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
        // 行级写保护挂在这里而不是 beforeUpdate：beforeSaveToDatabase 是所有落库路径的必经点
        // （BaseService.publish 直接调 updateToDatabase，不经过 beforeUpdate —— 挂在那里等于给发布动作留了缺口）
        enforceRowPermission(entity, entity.getId() == null ? DataPermissionAction.CREATE : DataPermissionAction.UPDATE);
        return beforeAppSaveToDatabase(entity);
    }

    /**
     * 写侧行级校验：既存行必须在当前主体的数据范围内
     *
     * <p>与 {@link #enforceDataPermission} 是两层不同的事实：那边问"这个动作配了策略没有"，
     * 这边问"<b>这一行</b>归不归你管"。缺了后者，"只能改本仓单据"这类规则在写侧是空的。</p>
     *
     * @param entity 待写入实体
     * @param action 写入动作
     */
    private void enforceRowPermission(@NotNull E entity, @NotNull DataPermissionAction action) {
        RowWriteGuard guard = SystemServices.getRowWriteGuard();
        if (guard == null) {
            // 容器未就绪（@Profile("!test")）或本资源不纳管：与 DataPermissionGuard 同口径直接返回
            return;
        }
        guard.enforce(entity, DataPermissionResourceResolver.resolve(getClass()), action);
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
        protectAttribution(result);
        enforceDataPermission(DataPermissionAction.UPDATE);
        // P3 通用操作审计：更新前回表取旧值，供 afterUpdate 记「改前」
        stashOperationLogBefore(result);
        return result;
    }

    /**
     * 归属列防篡改：更新时把 {@code tenant_id}/{@code creator_id} 恢复为库中既存值
     *
     * <p>§19.27 缺口③：普通用户可 PUT 把 {@code creatorId} 改成别人（把行送出自己视野）。
     * D1 已定归属只信服务端，所以这里不判「谁有权限改归属」——更新一律恢复库中值。
     * 新建不在这里处理：归属由 {@link TenantAwareEntity#fillAttribution()}（{@code @PrePersist}）
     * 无条件用服务端上下文覆盖。</p>
     *
     * <p>只对继承 {@link TenantAwareEntity} 的实体生效，且只在更新既有行时回表一次。
     * 取不到既存行直接放行：既存行都没了，归属恢复无从谈起（也不该拦——删除/重建的边界由别处管）。</p>
     *
     * @param entity 待更新实体
     */
    private void protectAttribution(@NotNull E entity) {
        if (!(entity instanceof TenantAwareEntity<?> owned)) {
            return;
        }
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        E stored = repository.findById(id).orElse(null);
        if (!(stored instanceof TenantAwareEntity<?> storedOwned)) {
            return;
        }
        AttributionWriteProtection.restoreOnUpdate(owned, storedOwned.getTenantId(), storedOwned.getCreatorId());
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
        enforceRowPermission(entity, DataPermissionAction.DELETE);
        // P3 通用操作审计：删除记「删掉的那条」
        recordOperationLog(OperationLogRecorder.ACTION_DELETE, entity.getId(), entity, null);
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
        // P3 通用操作审计：新增记「建出来的那条」
        recordOperationLog(OperationLogRecorder.ACTION_CREATE, id, null, source);
        afterAppAdd(id, source);
    }

    protected void afterAppAdd(long id, @NotNull E source) {
    }

    @Override
    protected void afterUpdate(long id, @NotNull E source) {
        // P3 通用操作审计：更新记「改前 + 改后」；旧值来自 beforeUpdate 暂存
        recordOperationLog(OperationLogRecorder.ACTION_UPDATE, id, operationLogBeforeSnapshot.get(), source);
        operationLogBeforeSnapshot.remove();
        afterAppUpdate(id, source);
    }

    protected void afterAppUpdate(long id, @NotNull E source) {
    }

    /**
     * 更新前把「旧值」暂存到线程局部，供 {@link #afterUpdate} 记「改前」。
     *
     * <p>只对标记了 {@link OperationLog} 的实体回表取旧值；未标记的零开销。</p>
     */
    private void stashOperationLogBefore(@NotNull E entity) {
        if (operationLogRecorder == null || !isOperationLogged(entity)) {
            return;
        }
        Long id = entity.getId();
        operationLogBeforeSnapshot.set(id == null ? null : repository.findById(id).orElse(null));
    }

    /**
     * 是否开启了该实体的通用操作审计。
     */
    private boolean isOperationLogged(@NotNull E entity) {
        return entity.getClass().isAnnotationPresent(OperationLog.class);
    }

    /**
     * 落一条通用操作审计。资源标识沿 {@link DataPermissionResourceResolver} 约定从 Service 类推导，
     * 与 {@code data_resource.resource_key} 一字不差；未开启审计或解析不到资源时静默返回。
     */
    private void recordOperationLog(@NotNull String action, Long targetId, E before, E after) {
        if (operationLogRecorder == null) {
            return;
        }
        E source = after != null ? after : before;
        if (source == null || !isOperationLogged(source)) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        operationLogRecorder.record(resourceKey, action, targetId, before, after);
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
        return CsvFormulaSanitizer.sanitize(ExportColumnMasker.maskForExport(entities));
    }
}

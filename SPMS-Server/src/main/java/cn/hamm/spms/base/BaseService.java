package cn.hamm.spms.base;

import cn.hamm.spms.base.permission.AttributionWriteProtection;
import cn.hamm.spms.base.spi.CodeRuleAllocator;
import cn.hamm.spms.base.spi.DataPermissionEnforcer;
import cn.hamm.spms.base.spi.ExportColumnProtector;
import cn.hamm.spms.base.spi.OperationLogSink;
import cn.hamm.spms.base.spi.RowPermissionEnforcer;
import cn.hamm.spms.base.spi.SensitiveFieldProtector;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.framework.curd.audit.OperationLog;
import cn.hamm.spms.framework.curd.base.CurdService;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionResourceResolver;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * <h1>基础服务类</h1>
 *
 * <p><b>本类与业务域的关系（2026-09-18 分层整改 L2b）</b>：底座要在"所有落库路径的必经点"上挂
 * 若干能力（自动取号、动作级/行级数据权限、受限字段写保护、导出遮蔽、操作审计），
 * 而这些能力的实现都属于 {@code module.*}。为了不出现"下层依赖上层"，它们一律走
 * {@link cn.hamm.spms.base.spi} 里的接口：<b>契约在 base，实现在业务域</b>，
 * 依赖方向是 {@code module → base}（允许的那一档）。整改前这里 import 了 9 个业务域类型，
 * 只能靠白名单放行。</p>
 *
 * <p>字段一律 {@code @Autowired(required = false)} 并在调用点判空，有两个真实原因：
 * ① 测试会直接 {@code new XxxService()}（不经过 Spring），那种实例上依赖本来就是 {@code null}；
 * ② 容器里没有该能力时应当"跳过"，与整改前的 {@code SystemServices.getXxx() == null} 语义一致。
 * <b>但 SPI 的实现类必须是常驻 Bean（不许条件装配）</b> —— 实现缺席等于静默少做一件事，
 * 而"少做的"往往是权限或脱敏。</p>
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
     * 自动编码取号（实现：{@code module.system.coderule.CodeRuleService}）
     */
    @Autowired(required = false)
    private CodeRuleAllocator codeRuleAllocator;

    /**
     * 动作级数据权限（实现：{@code module.system.datapermission.service.DataPermissionGuard}）
     */
    @Autowired(required = false)
    private DataPermissionEnforcer dataPermissionEnforcer;

    /**
     * 行级写保护（实现：{@code module.system.datapermission.service.RowWriteGuard}）
     */
    @Autowired(required = false)
    private RowPermissionEnforcer rowPermissionEnforcer;

    /**
     * 受限字段写保护（实现：{@code module.system.datapermission.service.SensitiveFieldProtectorAdapter}）
     */
    @Autowired(required = false)
    private SensitiveFieldProtector sensitiveFieldProtector;

    /**
     * 导出列遮蔽（实现：{@code module.system.datapermission.service.ExportColumnProtectorAdapter}）
     */
    @Autowired(required = false)
    private ExportColumnProtector exportColumnProtector;

    /**
     * 通用操作审计记录器（P3，实现：{@code module.system.datapermission.service.OperationLogRecorder}）。
     * 可选注入：容器未装配该 Bean 时，审计静默跳过，不影响任何业务路径。
     */
    @Autowired(required = false)
    private OperationLogSink operationLogSink;

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
        if (codeRuleAllocator != null) {
            codeRuleAllocator.fillFieldAutoCode(entity);
        }
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
     * <p>"本资源纳不纳管"由实现判定（未纳管的资源直接放行）—— 底座不复制那份登记表。</p>
     *
     * @param entity 待写入实体
     * @param action 写入动作
     */
    private void enforceRowPermission(@NotNull E entity, @NotNull DataPermissionAction action) {
        if (rowPermissionEnforcer == null) {
            return;
        }
        rowPermissionEnforcer.enforce(entity, DataPermissionResourceResolver.resolve(getClass()), action);
    }

    @Override
    protected E beforeAdd(@NotNull E entity) {
        // 敏感字段被提交成掩码时，新增没有原值可恢复 -> 明确拒绝（否则就是把 **** 建进库）
        if (sensitiveFieldProtector != null) {
            sensitiveFieldProtector.rejectMaskedOnAdd(entity);
        }
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
     * 新建不在这里处理：归属由 {@link TenantAwareEntity#fillAttribution()}（{@code KuboMetaObjectHandler}
     * 在 MyBatis-Plus 的 insert 钩子里调用）
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
        E stored = repository.selectById(id);
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
     * {@code module.system.datapermission.service.SensitiveWriteProtection}
     * （经 {@link SensitiveFieldProtector} 适配进来），这里只负责"把库中既存行交给判定方"。</p>
     *
     * <p>取数动作作为 {@link java.util.function.Supplier} 交出去，<b>由判定方决定要不要真取数</b>：
     * 两条识别路径都不命中时一次库都不查，正常保存仍是一次 UPDATE。</p>
     *
     * @param entity 待更新实体
     */
    private void protectSensitiveFields(@NotNull E entity) {
        if (sensitiveFieldProtector == null) {
            return;
        }
        Long id = entity.getId();
        if (id == null) {
            return;
        }
        sensitiveFieldProtector.restoreOnUpdate(
                entity,
                PermissionContextHolder.current().orElse(null),
                () -> repository.selectById(id)
        );
    }

    @Override
    protected void beforeDelete(@NotNull E entity) {
        super.beforeDelete(entity);
        enforceDataPermission(DataPermissionAction.DELETE);
        enforceRowPermission(entity, DataPermissionAction.DELETE);
        // P3 通用操作审计：删除记「删掉的那条」
        recordOperationLog(OperationLogSink.ACTION_DELETE, entity.getId(), entity, null);
    }

    /**
     * 数据权限写入守卫：校验当前主体是否拥有该资源对应写入动作的权限
     *
     * @param action 写入动作
     */
    protected void enforceDataPermission(@NotNull DataPermissionAction action) {
        if (dataPermissionEnforcer == null) {
            return;
        }
        String resourceKey = DataPermissionResourceResolver.resolve(getClass());
        if (resourceKey == null) {
            return;
        }
        dataPermissionEnforcer.enforce(resourceKey, action);
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
        recordOperationLog(OperationLogSink.ACTION_CREATE, id, null, source);
        afterAppAdd(id, source);
    }

    protected void afterAppAdd(long id, @NotNull E source) {
    }

    @Override
    protected void afterUpdate(long id, @NotNull E source) {
        // P3 通用操作审计：更新记「改前 + 改后」；旧值来自 beforeUpdate 暂存
        recordOperationLog(OperationLogSink.ACTION_UPDATE, id, operationLogBeforeSnapshot.get(), source);
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
        if (operationLogSink == null || !isOperationLogged(entity)) {
            return;
        }
        Long id = entity.getId();
        operationLogBeforeSnapshot.set(id == null ? null : repository.selectById(id));
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
        if (operationLogSink == null) {
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
        operationLogSink.record(resourceKey, action, targetId, before, after);
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
     * <p>遮蔽语义见 {@link ExportColumnProtector}：只改副本、按 QUERY 口径生成掩码、
     * 拿到 EXPORT 明文授权的列保留原值。</p>
     */
    @Override
    protected List<E> afterExportQuery(List<E> entities) {
        if (exportColumnProtector == null) {
            return CsvFormulaSanitizer.sanitize(entities);
        }
        return CsvFormulaSanitizer.sanitize(exportColumnProtector.protectForExport(entities));
    }
}

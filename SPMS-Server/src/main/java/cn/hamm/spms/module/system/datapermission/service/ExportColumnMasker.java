package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;

/**
 * <h1>导出内容的列级遮蔽</h1>
 *
 * <p><b>为什么必须单独做这一层</b>（结论来自 {@code javap} 反汇编，不是推测）：
 * 框架的导出链路是 {@code CurdService.afterExportQuery → CollectionUtil.getCsvValueList(fields, entities)
 * → ExportHelper.saveCsvListToFile(file, rows)}，取字段值走
 * {@code ReflectUtil.getFieldValue} / {@code BeanWrapper.getPropertyValue} ——
 * **全程不经过 Jackson**。所以 {@link cn.hamm.spms.module.system.datapermission.serializer.SensitiveFieldSerializer}
 * 那套"序列化时写掩码"对导出文件完全不生效，界面与文件必然不一致。
 * 而 {@code getCsvValueList}、{@code saveCsvListToFile} 都是静态方法，{@code createExportTask} 是 final，
 * 唯一可覆写的入口是 {@code protected List<E> afterExportQuery(List<E>)} —— 本类就挂在那里。</p>
 *
 * <p><b>与 {@link ExportBypassGuard} 的分工</b>：guard 在<b>请求线程</b>上决定"这次导出允不允许发生"
 * （enforce 下，受限角色缺少 EXPORT 明文授权就直接拒），本类决定"允许发生的导出里，每一列写什么值"。
 * 两者共用同一份策略来源（{@link ColumnMaskingService}）与同一份字段清单
 * （{@link SensitiveWriteProtection#bindingsFor}）—— 脱敏实现只允许存在一处，
 * 这里只决定"遮不遮"，掩码长什么样仍然只有 {@code ColumnMaskingService#applyMask} 一个出处。</p>
 *
 * <p><b>绝不修改入参实体</b>：导出查询返回的实体可能仍处在本事务的持久化上下文里，
 * 直接 {@code field.set(entity, mask)} 等于把 {@code 1******0} 写进数据库。
 * 因此每个需要改动的实体都先复制一份（{@link BeanUtils#copyProperties}），只改副本。
 * 复制不了（没有无参构造等）就抛错让导出失败 —— 宁可失败，不能为了"能导出"而放行明文。</p>
 *
 * <p><b>已知边界（不要读成已解决）</b>：上下文取自 {@link PermissionContextHolder}（线程级）。
 * 若框架把"查询 + 写文件"放进异步任务，这里就取不到上下文，本类会**记账并原样返回**：
 * 无上下文时"该给谁明文"没有答案，一律硬遮会误伤本就拿到 EXPORT 明文授权的人（大面积、静默），
 * 而准入判断已经由 guard 在请求线程上做过，enforce 下不会因此泄漏未授权明文。
 * 补齐上下文随任务传递（{@code TaskUtil} 装饰器或在 {@code beforeExportQuery} 里带快照）是下一步。</p>
 *
 * @author Hamm.cn
 */
public final class ExportColumnMasker {

    /**
     * 上下文缺失时的影子记账原因串（可被监控与人工排查引用，改名要同步文档）
     */
    public static final String REASON_EXPORT_WITHOUT_CONTEXT = "export-without-context";

    private ExportColumnMasker() {
    }

    /**
     * 对一批待导出的实体做列级遮蔽
     *
     * @param entities 框架导出查询拿到的实体列表（可能为 null / 空）
     * @param <E>      实体类型
     * @return 遮蔽后的列表；不需要处理时<b>原样返回入参引用</b>（不复制、不新建列表）
     */
    public static <E> List<E> maskForExport(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        E first = entities.get(0);
        if (first == null) {
            return entities;
        }
        List<SensitiveWriteProtection.Binding> bindings = SensitiveWriteProtection.bindingsFor(first.getClass());
        if (bindings.isEmpty()) {
            // 这个实体没有 @SensitiveField 字段：导出内容与权限无关，别为它建副本
            return entities;
        }
        DataPermissionProperties properties = DataPermissionRuntime.properties();
        ColumnMaskingService maskingService = DataPermissionRuntime.maskingService();
        if (properties == null || maskingService == null) {
            // 容器尚未就绪（或该链路跑在纯单测里）：与读侧一致，不遮也不炸
            return entities;
        }
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context != null && context.bypassDataPermission()) {
            // 数据权限旁路角色（系统账号）：界面本来就给他原值
            return entities;
        }
        if (context == null) {
            DataPermissionAudit audit = DataPermissionRuntime.audit();
            if (audit != null) {
                audit.shadow(bindings.get(0).resourceKey(), REASON_EXPORT_WITHOUT_CONTEXT,
                        "导出组装线程取不到数据权限上下文，本批 " + entities.size() + " 行按原值写出");
            }
            return entities;
        }

        List<E> out = new ArrayList<>(entities.size());
        for (E entity : entities) {
            out.add(entity == null ? null : maskOne(entity, bindings, maskingService, context));
        }
        return out;
    }

    /**
     * 单个实体：需要改任何一个字段时先在副本上改
     */
    private static <E> E maskOne(
            E entity,
            List<SensitiveWriteProtection.Binding> bindings,
            ColumnMaskingService maskingService,
            PermissionContext context
    ) {
        E copy = null;
        for (SensitiveWriteProtection.Binding binding : bindings) {
            String resourceKey = binding.resourceKey();
            if (DataPermissionProperties.Mode.OFF == maskingMode(resourceKey)) {
                continue;
            }
            if (grantsPlaintextExport(maskingService, resourceKey, binding.jsonName(), context)) {
                continue;
            }
            Object raw = readField(entity, binding);
            if (raw == null) {
                continue;
            }
            Object masked = maskingService.maskFieldValue(resourceKey, binding.jsonName(), raw, context);
            if (masked == raw || masked == null || masked.equals(raw)) {
                // 没有 QUERY 策略时 maskFieldValue 原样返回：说明这一列本来就不遮，别白造副本
                continue;
            }
            if (copy == null) {
                copy = copy(entity);
            }
            writeField(copy, binding, masked);
        }
        return copy == null ? entity : copy;
    }

    /**
     * 该字段是否拿到"导出可见明文"的授权
     *
     * <p>判据与 {@link ExportBypassGuard} 完全一致：EXPORT 动作下有一条 {@code allowPlaintext=true}
     * 的策略才算授权。两处各自解释会漂移，所以都用这个布尔语义而不是各写一份。</p>
     */
    static boolean grantsPlaintextExport(
            ColumnMaskingService maskingService, String resourceKey, String fieldName, PermissionContext context) {
        DataFieldPolicyEntity grant = maskingService
                .getEffectiveMaskPolicy(resourceKey, fieldName, ExportBypassGuard.ACTION_EXPORT, context);
        return grant != null && Boolean.TRUE.equals(grant.getAllowPlaintext());
    }

    private static DataPermissionProperties.Mode maskingMode(String resourceKey) {
        DataPermissionProperties properties = DataPermissionRuntime.properties();
        // 句柄为 null 只可能是容器还没起来（调用方已挡），这里退到最严的一侧而不是放开
        return properties == null ? DataPermissionProperties.Mode.ENFORCE : properties.effectiveMode(resourceKey);
    }

    private static <E> Object readField(E entity, SensitiveWriteProtection.Binding binding) {
        try {
            return binding.field().get(entity);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(
                    "导出遮蔽失败：无法读取 " + entity.getClass().getSimpleName() + '.' + binding.jsonName()
                            + "（@SensitiveField 字段应已由扫描设为可访问）", error);
        }
    }

    private static <E> void writeField(E target, SensitiveWriteProtection.Binding binding, Object value) {
        try {
            binding.field().set(target, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(
                    "导出遮蔽失败：无法写入 " + target.getClass().getSimpleName() + '.' + binding.jsonName(), error);
        }
    }

    private static <E> E copy(E entity) {
        Class<?> type = entity.getClass();
        try {
            E instance = (E) BeanUtils.instantiateClass(type);
            BeanUtils.copyProperties(entity, instance);
            return instance;
        } catch (RuntimeException error) {
            // 不静默回退成"改原对象"：那正是把掩码写进库的形状
            throw new IllegalStateException(
                    "导出遮蔽需要复制实体（避免把掩码写回库），但 " + type.getName()
                            + " 无法按无参构造复制：" + error.getMessage(), error);
        }
    }
}

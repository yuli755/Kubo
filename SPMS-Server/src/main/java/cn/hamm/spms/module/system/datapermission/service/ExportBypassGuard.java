package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * <h1>导出旁路的写侧收口</h1>
 *
 * <p><b>为什么需要它</b>：列级脱敏做在 Jackson 序列化层，而导出走文件流、<b>不经过 Jackson</b>。
 * 于是"这个角色的价格列被遮蔽了"在列表里成立、在导出里不成立 —— 一份 xlsx 就能绕过全部列级策略。
 * 这类旁路最危险的地方不是"没做"，而是"看起来做了"：策略界面上 EXPORT 动作一直是个可选项，
 * 管理员配完之后防护其实仍是零。</p>
 *
 * <p><b>为什么这一轮做"拒绝"而不是"给导出也加遮蔽"</b>：导出实现由框架
 * （{@code Curd.Export} / {@code Curd.QueryExport}）生成，本项目看不到也改不了它的行组装；
 * 自己再写一套导出 = 第二套真相，与"消灭第二套实现"的改造目标直接冲突。
 * 所以这里把旁路变成<b>明确拒绝</b>，并让 {@code EXPORT} 动作真的有意义：
 * 受限角色要导出，必须逐个字段显式配一条 {@code actionName=EXPORT, allowPlaintext=true} 的策略
 * —— 也就是"我明确同意把这一列的明文交给这个角色的文件"。</p>
 *
 * <p><b>判定规则</b>（资源键由 {@code data_resource.table_name} 反查，绝不在代码里猜映射）：</p>
 * <ol>
 *   <li>非导出请求 / 无权限上下文 / 系统旁路身份 → 直接放行；</li>
 *   <li>该资源的 {@code QUERY} 策略对这个用户没有任何遮蔽 → 放行（他没被限制，导出自然也无需限制）；</li>
 *   <li>被遮蔽的<b>每一个</b>字段都在 {@code EXPORT} 动作下拿到 {@code allowPlaintext} → 放行；
 *       少一个字段就拦：文件里只要有一列是明文，这次导出就已经泄了；</li>
 *   <li>{@code mode=off} → 不介入（回滚开关必须真的能回滚）；
 *       {@code mode=shadow} → 只记影子差异（{@code export-bypass}），不改变结果，
 *       让灰度期能先看清"开了 enforce 会挡住多少人导出"；</li>
 *   <li>{@code mode=enforce} → 拒绝，报错写清是哪几列、以及怎么放开。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class ExportBypassGuard {

    /**
     * 框架在 {@code @Extends({Curd.Export, Curd.QueryExport})} 下暴露的两个导出动作路径段
     */
    private static final Set<String> EXPORT_SEGMENTS = Set.of("export", "queryExport");

    /**
     * 列级策略里代表"导出"的动作名（与 {@code data_resource.supported_actions} 的取值一致）
     */
    public static final String ACTION_EXPORT = "EXPORT";

    /**
     * 影子记账用的原因串
     */
    public static final String REASON_EXPORT_BYPASS = "export-bypass";

    @Autowired
    private DataResourceRepository dataResourceRepository;

    @Autowired
    private ColumnMaskingService columnMaskingService;

    @Autowired
    private DataPermissionAudit dataPermissionAudit;

    /**
     * 拦截一次请求：只在"导出且该用户受限且没给明文授权"时阻断
     *
     * @param request 当前请求（用 URI 判动作与资源）
     */
    public void guard(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String action = exportActionOf(uri);
        if (action == null) {
            return;
        }

        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            // 无上下文（内部调用）或系统旁路身份（root / DATA_PERMISSION_BYPASS）：不改变既有行为
            return;
        }

        String apiSegment = apiSegmentOf(uri);
        if (apiSegment == null) {
            return;
        }
        DataResourceEntity resource = dataResourceRepository.getByTableName(apiSegment);
        if (resource == null || resource.getResourceKey() == null) {
            // 未注册资源 = 没有任何列级策略可言，拦它只会误伤（真正的 fail-closed 在行权限那侧）
            return;
        }
        String resourceKey = resource.getResourceKey();

        DataPermissionProperties properties = DataPermissionRuntime.properties();
        if (properties == null || properties.effectiveMode(resourceKey) == DataPermissionProperties.Mode.OFF) {
            return;
        }

        Set<String> restricted = restrictedFields(resourceKey, context);
        if (restricted.isEmpty()) {
            return;
        }
        if (allGrantedForExport(resourceKey, restricted, context)) {
            return;
        }

        String detail = "fields=" + String.join(",", restricted);
        if (!properties.enforced(resourceKey)) {
            // 观察期：只记账不改变结果，否则灰度本身变成了新的故障源
            dataPermissionAudit.shadow(resourceKey, REASON_EXPORT_BYPASS, detail);
            log.info("[shadow] 导出旁路本应被拦截：resource={} {}（放开 enforce 前请先补 EXPORT 明文策略）",
                    resourceKey, detail);
            return;
        }
        // 以前这里是框架的 show(msg)：状态码对，但真拒不进 deny 计数（只有 shadow 分支记了一笔），
        // 于是"放开后挡了多少人导出"这个数在指标里恒为 0，只能翻日志。改成与写侧同一个出口。
        DataPermissionDenial.denied(dataPermissionAudit, resourceKey, REASON_EXPORT_BYPASS,
                "导出被拒：" + detail,
                String.format("%s 的 %s 列对当前角色已按数据权限策略遮蔽，导出文件不经过脱敏、会原样带出明文，"
                                + "因此本次导出被拒绝。确需导出请在【数据权限-字段策略】为这些字段配置 EXPORT 动作的明文授权",
                        resourceKey, String.join("、", restricted)));
    }

    /**
     * 该 URI 是否为导出请求（返回动作段，非导出返回 null）
     *
     * <p>只认"最后一段是 export/queryExport"，不用 {@code contains}：否则
     * {@code /exportLog/list} 这类业务路径会被误判成导出。</p>
     */
    static String exportActionOf(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int last = path.lastIndexOf('/');
        if (last < 0 || last == path.length() - 1) {
            return null;
        }
        String segment = path.substring(last + 1);
        for (String candidate : EXPORT_SEGMENTS) {
            if (candidate.equalsIgnoreCase(segment)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 取导出请求里的实体段（{@code /api/material/export} → {@code material}）
     */
    static String apiSegmentOf(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int last = path.lastIndexOf('/');
        if (last <= 0) {
            return null;
        }
        String withoutAction = path.substring(0, last);
        int prev = withoutAction.lastIndexOf('/');
        if (prev < 0 || prev == withoutAction.length() - 1) {
            return null;
        }
        String segment = withoutAction.substring(prev + 1);
        // 前端 axios 的 baseURL=/api 只是代理前缀：只剩 api 说明路径不完整（如 /api/export），
        // 认不出实体就不拦（拿表名去查资源会查空，但别把"猜不出来"当成"应当拒绝"）
        return "api".equalsIgnoreCase(segment) ? null : segment;
    }

    /**
     * 该用户在这个资源上被遮蔽的字段（有 QUERY 策略且未开明文）
     */
    Set<String> restrictedFields(String resourceKey, PermissionContext context) {
        Set<String> fields = new LinkedHashSet<>();
        Map<String, DataFieldPolicyEntity> policies =
                columnMaskingService.getEffectiveMaskPolicyMap(resourceKey, context);
        for (Map.Entry<String, DataFieldPolicyEntity> entry : policies.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue().getAllowPlaintext())) {
                fields.add(entry.getKey());
            }
        }
        return fields;
    }

    /**
     * 当前用户在某资源上的"导出可见性"：受限字段名 → 是否已拿到导出明文授权
     *
     * <p>为什么由后端返回<b>结论</b>而不是把策略原文丢给前端自己判：这套布尔语义
     * （"EXPORT 动作下有一条 {@code allowPlaintext=true} 才算授权"）已经有两处实现 ——
     * {@link #allGrantedForExport} 与 {@link ExportColumnMasker#grantsPlaintextExport}。
     * 再让浏览器实现第三份，就一定出现"提示说可以、后端把请求拒了"这类漂移，
     * 而提示一旦不可信，人们会直接忽略它 —— 比没有提示更糟。</p>
     *
     * <p>只列<b>受限字段</b>（QUERY 下会被遮的那些）：没被遮的字段导出必然原值，
     * 列出来只会让界面噪声变大、把真正的缺口埋掉。</p>
     *
     * @param resourceKey 数据资源标识
     * @param context     当前权限上下文；{@code null} 时返回空表（不猜"能看"也不猜"会被拒"）
     * @return 字段名 → 是否可明文导出；空表表示该用户在这个资源上没有受限字段
     */
    public Map<String, Boolean> exportVisibility(String resourceKey, PermissionContext context) {
        Map<String, Boolean> visibility = new LinkedHashMap<>();
        if (context == null) {
            return visibility;
        }
        for (String field : restrictedFields(resourceKey, context)) {
            visibility.put(field, ExportColumnMasker.grantsPlaintextExport(
                    columnMaskingService, resourceKey, field, context));
        }
        return visibility;
    }

    /**
     * 被遮蔽的字段是否<b>全部</b>拿到了 EXPORT 明文授权
     *
     * <p>这里刻意用"每一个"而不是"存在一个"：导出是一整份文件，
     * 只要有一列没授权，这份文件就是泄漏 —— 部分放行等于没管。</p>
     */
    boolean allGrantedForExport(String resourceKey, Set<String> fields, PermissionContext context) {
        for (String field : fields) {
            DataFieldPolicyEntity policy =
                    columnMaskingService.getEffectiveMaskPolicy(resourceKey, field, ACTION_EXPORT, context);
            if (policy == null || !Boolean.TRUE.equals(policy.getAllowPlaintext())) {
                return false;
            }
        }
        return !fields.isEmpty();
    }
}

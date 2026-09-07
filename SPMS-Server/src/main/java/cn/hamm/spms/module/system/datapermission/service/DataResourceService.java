package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.rule.OwnerColumnRegistration;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleSupport;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>数据资源管理服务</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class DataResourceService extends BaseService<DataResourceEntity, DataResourceRepository> {
    private final DataPermissionEventPublisher eventPublisher;

    /**
     * 走 provider 接口而不是直接注入实现：列级策略的取数路径（含缓存）只该有一份，
     * 自己 new 一个 repository 查询就等于绕过缓存并另起一套"什么算启用策略"的解释。
     */
    private final DataFieldPolicyProvider fieldPolicyProvider;

    /**
     * 行级策略取数（盘点专用）
     */
    private final DataPolicyService rowPolicyService;

    /**
     * 只用来读 JPA 元模型（表名 → 实体形状），不参与任何查询
     */
    private final EntityManagerFactory entityManagerFactory;

    /**
     * 灰度配置的<em>唯一</em>读取口：状态面板不许自己算"名单里有没有它"，
     * 必须问同一份 effectiveMode —— 否则面板报的档位与真实生效的档位会各自漂移
     */
    private final DataPermissionProperties properties;

    /**
     * 只为枚举 {@link BaseService} 的 Bean（找出"还没纳管的服务"），不参与任何业务调用
     */
    private final ApplicationContext applicationContext;

    public DataResourceService(DataPermissionEventPublisher eventPublisher,
                               DataFieldPolicyProvider fieldPolicyProvider,
                               DataPolicyService rowPolicyService,
                               EntityManagerFactory entityManagerFactory,
                               DataPermissionProperties properties,
                               ApplicationContext applicationContext) {
        this.eventPublisher = eventPublisher;
        this.fieldPolicyProvider = fieldPolicyProvider;
        this.rowPolicyService = rowPolicyService;
        this.entityManagerFactory = entityManagerFactory;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * 一条"这一档维度在这个资源上没有列可绑"的结论
     *
     * @param resourceId   资源 ID
     * @param resourceKey  资源标识
     * @param subjectType  受影响的维度；{@code ENTITY_NOT_MAPPED} 时为 null（整表无映射）
     * @param kind         类别（与 {@link DimensionBindingAnalyzer} 的常量一致）
     * @param detail       人话说明（含建议写法）
     */
    public record DimensionBindingRiskItem(Long resourceId,
                                          String resourceKey,
                                          String subjectType,
                                          String kind,
                                          String detail) {
    }

    /**
     * 一个资源「此刻实际是什么档」的只读快照
     *
     * @param resourceId       资源 ID
     * @param resourceKey      资源标识（人认这个；资源表没有"名称"列，别在这里造一个）
     * @param resourceStatus   资源启用状态（ENABLED / 其它）
     * @param effectiveMode    生效模式，取自 {@link DataPermissionProperties#effectiveMode(String)}
     * @param inEnforceList    是否出现在 {@code enforce-resources} 名单里（只解释原因，不参与判定）
     * @param rowWriteGuarded  写侧行级保护此刻是否会真的去问探针（{@code row-write.enabled} 且该资源参与计算）
     * @param queryPolicyCount 启用中的 QUERY 行级策略数
     * @param writePolicyCount 启用中的 UPDATE + DELETE 行级策略数
     * @param exportPolicyCount 启用中的 EXPORT 行级策略数
     * @param fieldPolicyCount 启用中的列级策略数
     * @param bindingRiskCount 维度可绑定性风险条数（与 {@link #dimensionBindingRisks()} 同一份实现）
     * @param notes            后端算好的人话提示；<b>前端不许自己再判一遍</b>（判据只该有一份）
     */
    public record ResourceRuntimeStatus(Long resourceId,
                                       String resourceKey,
                                       String resourceStatus,
                                       String effectiveMode,
                                       boolean inEnforceList,
                                       boolean rowWriteGuarded,
                                       long queryPolicyCount,
                                       long writePolicyCount,
                                       long exportPolicyCount,
                                       long fieldPolicyCount,
                                       long bindingRiskCount,
                                       List<String> notes) {

        /**
         * 列表渲染友好：没有提示时返回空列表而不是 null
         */
        public ResourceRuntimeStatus {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    /**
     * 灰度状态总览
     *
     * @param globalMode                   配置里的全局模式
     * @param enforceResources             逐资源灰度名单（顺序保持配置里的写法）
     * @param denyUnregistered             未登记资源是否默认拒绝
     * @param rowWriteEnabled              写侧行级保护总开关
     * @param hierarchyDepartmentEnabled   部门层级闭包是否展开
     * @param unmanagedServiceKeys         {@code BaseService} 派生出了资源键、但库里没有的服务键清单
     * @param resources                    各资源状态
     */
    public record RuntimeStatus(String globalMode,
                                Set<String> enforceResources,
                                boolean denyUnregistered,
                                boolean rowWriteEnabled,
                                boolean hierarchyDepartmentEnabled,
                                List<String> unmanagedServiceKeys,
                                List<ResourceRuntimeStatus> resources) {
    }

    /**
     * 汇总"当前配置 + 各资源实际生效档位"，供管理端灰度面板使用
     *
     * <p>为什么需要它：模式与灰度名单在配置文件里，策略在数据库里，而页面上<em>哪一头都看不到</em>
     * "这个资源此刻到底是 shadow 还是 enforce"。灰度推进最怕的就是看不清自己站在哪一格 ——
     * 于是"逐资源放开"这一步只能靠翻 yml + 猜名单交集，猜错的代价直接落在生产。</p>
     *
     * <p>档位一律取 {@link DataPermissionProperties#effectiveMode(String)}，<b>不在这里重算名单逻辑</b>：
     * {@code mode=ENFORCE} 时名单是"例外清单"（名单外的降回 shadow），{@code mode=SHADOW} 时才是
     * "提前放开清单"。这段规则一旦被抄第二遍，面板就会开始骗人，而骗人的面板比没有面板更糟
     * （管理员会照着它做决策）。</p>
     *
     * <p>代价：每个资源各一次策略取数 + 一次列级策略取数（当前 24 个资源，约 50 条查询），
     * 只在管理端打开面板时发生；{@code dimensionBindingRisks()} 也重算一遍以保证两处口径同源。
     * 这个端点因此是<em>只读、低频、不参与任何判定</em>的 —— 它不能变成第二把尺子。</p>
     *
     * @return 总览
     */
    public RuntimeStatus runtimeStatus() {
        List<DataResourceEntity> all = repository.findAll();
        Set<String> registeredKeys = new LinkedHashSet<>();
        if (all != null) {
            for (DataResourceEntity resource : all) {
                if (resource != null && resource.getResourceKey() != null) {
                    registeredKeys.add(resource.getResourceKey());
                }
            }
        }
        Map<String, Long> riskCounts = new LinkedHashMap<>();
        for (DimensionBindingRiskItem risk : dimensionBindingRisks()) {
            riskCounts.merge(risk.resourceKey(), 1L, Long::sum);
        }

        List<ResourceRuntimeStatus> items = new ArrayList<>();
        if (all != null) {
            for (DataResourceEntity resource : all) {
                if (resource == null || resource.getResourceKey() == null) {
                    continue;
                }
                items.add(statusOf(resource, riskCounts.getOrDefault(resource.getResourceKey(), 0L)));
            }
        }
        return new RuntimeStatus(
                String.valueOf(properties.getMode()),
                new LinkedHashSet<>(properties.getEnforceResources()),
                properties.isDenyUnregistered(),
                properties.getRowWrite().isEnabled(),
                properties.getHierarchy().isDepartmentEnabled(),
                unmanagedServiceKeys(registeredKeys),
                items
        );
    }

    /**
     * 「这个资源此刻该注意什么」——提示的判据（静态，可脱离 Spring 单测）
     *
     * <p>为什么由后端算：这些句子讲的是配置与策略数量之间的<em>关系</em>（名单语义、未登记怎么处理、
     * 绑不上列会退化成恒假）。前端算一遍就是抄第二份判据，而面板一旦开始骗人，管理员会照着它
     * 决定要不要放开下一个资源 —— 比没有面板更糟。与 {@link #validateRegistrations} 同样做成静态方法：
     * 判据必须能在不起容器的情況下测。</p>
     *
     * @param resource         资源行
     * @param mode             该资源此刻的生效模式（已由 effectiveMode 算好）
     * @param queryPolicyCount 启用中的 QUERY 行级策略数
     * @param writePolicyCount 启用中的 UPDATE + DELETE 行级策略数
     * @param bindingRiskCount 维度可绑定性风险条数
     * @param properties       灰度配置
     * @return 提示列表；没有问题时为空（不制造噪音，否则面板会被整屏忽略）
     */
    static List<String> runtimeNotes(DataResourceEntity resource,
                                     DataPermissionProperties.Mode mode,
                                     long queryPolicyCount,
                                     long writePolicyCount,
                                     long bindingRiskCount,
                                     DataPermissionProperties properties) {
        List<String> notes = new ArrayList<>();
        boolean enabled = "ENABLED".equals(resource.getStatus());
        boolean inList = resource.getResourceKey() != null
                && properties.getEnforceResources().contains(resource.getResourceKey());
        if (!enabled) {
            notes.add("资源未启用：读写两侧都按未登记处理，当前 deny-unregistered="
                    + properties.isDenyUnregistered() + "（" + (properties.isDenyUnregistered() ? "会拒" : "会放行") + "）");
        }
        if (writePolicyCount == 0L && mode == DataPermissionProperties.Mode.ENFORCE) {
            notes.add("已进 enforce 名单但没有任何 UPDATE/DELETE 策略：DataPermissionGuard 会把该资源的保存与删除直接拒掉，先补策略");
        }
        if (writePolicyCount == 0L && mode == DataPermissionProperties.Mode.SHADOW) {
            notes.add("尚无 UPDATE/DELETE 策略：放开前必须先补，否则一进名单保存就报错");
        }
        if (queryPolicyCount == 0L && properties.isDenyUnregistered()) {
            notes.add("deny-unregistered=true 且没有 QUERY 策略：该资源的查询会被恒假条件挡住（查空）");
        }
        if (inList && properties.getMode() == DataPermissionProperties.Mode.ENFORCE
                && !properties.getEnforceResources().isEmpty()) {
            notes.add("全局已是 enforce：名单在这里是「例外清单」，出现在名单里并不代表变化");
        }
        if (bindingRiskCount > 0L) {
            notes.add("有 " + bindingRiskCount + " 条维度绑不上列：enforce 下这些条件会退化成恒假（查空），先在「维度可绑定性」面板清零");
        }
        return notes;
    }

    /**
     * 单个资源的状态行（策略计数与提示都在这里算完，前端只负责显示）
     */
    private ResourceRuntimeStatus statusOf(DataResourceEntity resource, long bindingRiskCount) {
        String resourceKey = resource.getResourceKey();
        DataPermissionProperties.Mode mode = properties.effectiveMode(resourceKey);
        boolean inList = properties.getEnforceResources().contains(resourceKey);

        long query = 0L;
        long write = 0L;
        long export = 0L;
        List<DataPolicyEntity> policies = resource.getId() == null
                ? List.of() : rowPolicyService.getEnabledPoliciesByResource(resource.getId());
        for (DataPolicyEntity policy : policies) {
            String action = policy.getActionName();
            if (DataPermissionAction.QUERY.name().equals(action)) {
                query++;
            } else if (DataPermissionAction.UPDATE.name().equals(action)
                    || DataPermissionAction.DELETE.name().equals(action)) {
                write++;
            } else if (DataPermissionAction.EXPORT.name().equals(action)) {
                export++;
            }
        }
        long fieldCount = fieldPolicyProvider.getEnabledPoliciesByResource(resourceKey).size();

        List<String> notes = runtimeNotes(resource, mode, query, write, bindingRiskCount, properties);
        return new ResourceRuntimeStatus(
                resource.getId(),
                resourceKey,
                resource.getStatus(),
                mode.name(),
                inList,
                properties.getRowWrite().isEnabled() && properties.computes(resourceKey),
                query,
                write,
                export,
                fieldCount,
                bindingRiskCount,
                notes
        );
    }

    /**
     * 找出「派生出了资源键、但库里没有这条 data_resource」的服务
     *
     * <p>{@code BaseService} 的三个写钩子会按类名推出资源键（{@code wms/InputService} → {@code wms.input}）：
     * 库里没有这一行时，写侧按 {@code deny-unregistered} 处理。<b>不加这个清单，"这个模块其实还没纳管"
     * 这件事在系统里没有任何地方能看见</b> —— 直到有人改配置把 {@code deny-unregistered} 打开，
     * 才突然一片 500。资源键推不出来的服务（不在 {@code module.*} 下、名字不以 Service 结尾）不参与统计。</p>
     *
     * @param registeredKeys 库里已登记的资源键
     * @return 未纳管的资源键，按字典序
     */
    private List<String> unmanagedServiceKeys(Set<String> registeredKeys) {
        Set<String> missing = new TreeSet<>();
        String[] beanNames;
        try {
            beanNames = applicationContext.getBeanNamesForType(BaseService.class);
        } catch (RuntimeException e) {
            log.warn("灰度状态面板跳过未纳管服务枚举：{}", e.getMessage());
            return List.of();
        }
        for (String beanName : beanNames) {
            Class<?> type;
            try {
                type = applicationContext.getType(beanName);
            } catch (RuntimeException e) {
                continue;
            }
            String key = DataPermissionResourceResolver.resolve(type);
            if (key != null && !registeredKeys.contains(key)) {
                missing.add(key);
            }
        }
        return new ArrayList<>(missing);
    }

    /**
     * 已启用的行级策略里，"实体上找不到对应列"的那些维度
     *
     * <p>与启动门禁 {@code FactoryDimensionReadiness} 的取向刻意相反：那边信息不足必须倒向「不通过」，
     * 这边拿不到元模型就返回空清单并打一条 WARN —— 展示型盘点若在信息不足时把 24 个资源全报成
     * "表名没对上"，真问题会被淹掉。同理，<b>没有启用策略的资源不参与判定</b>：没配这一档却被报
     * "这一档绑不上"是噪音，而噪音会让整个面板失去可信度。</p>
     *
     * @return 风险条目；无问题为空列表
     */
    public List<DimensionBindingRiskItem> dimensionBindingRisks() {
        Collection<Class<?>> entities = EntityShapeIndex.entityClasses(entityManagerFactory);
        if (entities.isEmpty()) {
            log.warn("维度可绑定性盘点跳过：拿不到 JPA 元模型（EntityManagerFactory 未就绪）");
            return List.of();
        }
        Map<String, Class<?>> byTable = EntityShapeIndex.byTable(entities);
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<DimensionBindingAnalyzer.Row> rows = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            Long resourceId = resource.getId();
            if (resourceId == null) {
                continue;
            }
            List<DataPolicyEntity> policies = rowPolicyService.getEnabledPoliciesByResource(resourceId);
            if (policies.isEmpty()) {
                continue;
            }
            List<String> subjectTypes = new ArrayList<>();
            for (DataPolicyEntity policy : policies) {
                subjectTypes.add(policy.getSubjectType());
            }
            Class<?> entity = byTable.get(EntityShapeIndex.normalizeTableName(resource.getTableName()));
            // 有 @Table 的类才进得来这个索引；表名写错时 entity 为 null，
            // 交给分析器报「实体未映射」，而不是在这里当成"所有列都不存在"刷一堆假问题
            EntityShapeIndex.Shape shape = EntityShapeIndex.shapeOf(entity);
            rows.add(new DimensionBindingAnalyzer.Row(resourceId, resource.getResourceKey(), resource.getTableName(),
                    resource.getTenantColumn(), resource.getOwnerColumns(), shape,
                    DimensionBindingAnalyzer.countBySubjectType(subjectTypes)));
        }
        List<DimensionBindingRiskItem> items = new ArrayList<>();
        for (DimensionBindingAnalyzer.Risk risk : DimensionBindingAnalyzer.risks(rows)) {
            items.add(new DimensionBindingRiskItem(risk.resourceId(), risk.resourceKey(), risk.subjectType(),
                    risk.kind(), risk.detail()));
        }
        return items;
    }

    /**
     * 一条"永不命中"的行级策略（带资源定位）
     *
     * @param policyId    策略行主键
     * @param resourceId  数据资源 ID
     * @param resourceKey 数据资源标识
     * @param policyCode  策略编码（人认这个）
     * @param kind        风险类别（与 {@link RowPolicyRiskAnalyzer} 的三个常量一致）
     * @param detail      人话说明
     */
    public record RowPolicyRiskItem(Long policyId,
                                    Long resourceId,
                                    String resourceKey,
                                    String policyCode,
                                    String kind,
                                    String detail) {
    }

    /**
     * 行级策略里"启用着但永远不会命中"的存量行
     *
     * <p>为什么这件事值得单独盘：行级规则不命中的后果不是少个提示，而是<b>该主体在这个资源上没有防护</b>，
     * 而策略列表里它显示"已启用"。写错动作名、少填 subjectId、给还没接入的 FACTORY 配策略，
     * 三种都是保存成功的。</p>
     *
     * <p>工厂维度的开关从 {@code DataPermissionRuntime} 取（与 {@code DimensionGuard} 同一入口，
     * 保证"面板说的"和"写入侧拦的"是同一个事实）；取不到配置时按<b>未接入</b>处理，
     * 因为默认值就是未接入 —— 宁可按现实报，也不按愿望报。</p>
     *
     * @return 风险条目列表；无问题时为空列表
     */
    public List<RowPolicyRiskItem> rowPolicyRisks() {
        boolean factoryWired = DimensionGuard.isFactoryAvailable();
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<RowPolicyRiskItem> items = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            Long resourceId = resource.getId();
            String resourceKey = resource.getResourceKey();
            if (resourceId == null || resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            for (RowPolicyRiskAnalyzer.Risk risk : RowPolicyRiskAnalyzer.risks(
                    rowPolicyService.getEnabledPoliciesByResource(resourceId), factoryWired)) {
                items.add(new RowPolicyRiskItem(risk.policyId(), resourceId, resourceKey,
                        risk.policyCode(), risk.kind(), risk.detail()));
            }
        }
        return items;
    }

    /**
     * 一条导出缺口（带"它属于哪个资源"）
     *
     * <p>为什么把资源标识摊平进每条缺口而不是返回 {@code Map<resourceKey, List<Gap>>}：
     * 这份数据有两个消费方 —— 资源列表页按资源分组汇总，策略编辑器按
     * {@code (resourceId, fieldName)} 定位当前这条策略是否处在缺口里。
     * 编辑器手上只有 {@code resourceId}（表单就提交这个字段），返回 Map 就等于逼前端
     * 再去查一次"id 对应哪个 key"：多一次请求、多一处会写歪的映射。</p>
     *
     * @param resourceId  数据资源 ID（与表单里的 resourceId 同域，可直接比对）
     * @param resourceKey 数据资源标识（人看的）
     * @param fieldName   被遮蔽却缺导出明文授权的字段
     * @param roleCode    生效角色；{@code default} 表示对所有角色兜底
     * @param reason      人话说明（由后端给，前端不重算语义）
     */
    /**
     * 一条"没人读"的列级策略（带资源定位）
     *
     * @param policyId   策略行主键
     * @param resourceId 数据资源 ID
     * @param resourceKey 数据资源标识
     * @param fieldName  字段名
     * @param roleCode   角色编码
     * @param actionName 动作名（原样回显，可能正是拼错的那个值）
     */
    public record InertPolicyItem(Long policyId,
                                  Long resourceId,
                                  String resourceKey,
                                  String fieldName,
                                  String roleCode,
                                  String actionName) {
    }

    /**
     * 列级策略表里"动作不会被任何代码消费"的存量行
     *
     * <p>只读、只报，不提供"顺手停用"：存量行是历史决定的产物，批量改动需要有人先确认
     * 这些行的来源（导入脚本？早期灰度？），那是数据治理决策而不是这个面板的职责。</p>
     *
     * @return 条目列表；无存量问题时为空列表
     */
    public List<InertPolicyItem> inertPolicies() {
        // 消费集取自后端常量本身，而不是又抄一份名单 —— 前端那份由 maskContract 对账
        Set<String> consumed = Set.of(ColumnMaskingService.DEFAULT_ACTION, ExportBypassGuard.ACTION_EXPORT);
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<InertPolicyItem> items = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            String resourceKey = resource.getResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            for (FieldPolicyGapAnalyzer.Inert inert : FieldPolicyGapAnalyzer.inertPolicies(
                    fieldPolicyProvider.getEnabledPoliciesByResource(resourceKey), consumed)) {
                items.add(new InertPolicyItem(inert.id(), resource.getId(), resourceKey,
                        inert.fieldName(), inert.roleCode(), inert.actionName()));
            }
        }
        return items;
    }

    public record ExportGapItem(Long resourceId,
                                String resourceKey,
                                String fieldName,
                                String roleCode,
                                String reason) {
    }

    /**
     * 各资源的"导出明文授权"缺口（管理端自查用）
     *
     * <p>为什么挂在这里而不是新开一个公开端点：这条信息是<b>跨角色</b>的策略地图，
     * 普通用户读到它就能推断"哪个角色的哪个字段有明文"。挂在数据资源管理（本就只对该资源
     * 有查询权限的管理员可见）之下，权限面不扩大。</p>
     *
     * @return 缺口条目列表（只含有缺口的资源，按资源登记顺序）
     */
    public List<ExportGapItem> exportGaps() {
        List<DataResourceEntity> resources = repository.findAll();
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        List<ExportGapItem> items = new ArrayList<>();
        for (DataResourceEntity resource : resources) {
            String resourceKey = resource.getResourceKey();
            if (resourceKey == null || resourceKey.isBlank()) {
                continue;
            }
            List<DataFieldPolicyEntity> masking = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ColumnMaskingService.DEFAULT_ACTION);
            List<DataFieldPolicyEntity> grants = fieldPolicyProvider
                    .getEnabledPoliciesByResourceAndAction(resourceKey, ExportBypassGuard.ACTION_EXPORT);
            for (FieldPolicyGapAnalyzer.Gap gap : FieldPolicyGapAnalyzer.gaps(masking, grants)) {
                items.add(new ExportGapItem(resource.getId(), resourceKey, gap.fieldName(), gap.roleCode(), gap.reason()));
            }
        }
        return items;
    }

    @Override
    protected void afterAppUpdate(long id, DataResourceEntity entity) {
        super.afterAppUpdate(id, entity);
        eventPublisher.publishResourceChanged(entity.getResourceKey());
        log.info("发布资源更新事件: {}", entity.getResourceKey());
    }

    @Override
    protected void beforeDelete(DataResourceEntity entity) {
        super.beforeDelete(entity);
        // 在删除前发布事件
        if (entity != null) {
            eventPublisher.publishResourceChanged(entity.getResourceKey());
            log.info("发布资源删除事件: {}", entity.getResourceKey());
        }
    }

    @Override
    protected DataResourceEntity beforeAdd(@NotNull DataResourceEntity entity) {
        validateRegistrations(entity);
        return super.beforeAdd(entity);
    }

    @Override
    protected DataResourceEntity beforeUpdate(@NotNull DataResourceEntity entity) {
        validateRegistrations(entity);
        return super.beforeUpdate(entity);
    }

    /**
     * 保存期把登记串拦下来：配错的代价是"策略看着生效了、其实一行也查不到"
     *
     * <p>语法判据只有后端那一份（{@link OwnerColumnRegistration}），本方法不重述规则、只做拦截；
     * 前端编辑器里的说明文字是给人看的，不能当校验器（{@code maskContract.spec.ts} 里有一条门禁
     * 盯着"前端不许自己解析登记串"）。</p>
     *
     * @param entity 待保存的资源
     */
    static void validateRegistrations(DataResourceEntity entity) {
        if (entity == null) {
            return;
        }
        String malformed = OwnerColumnRegistration.validate(entity.getOwnerColumns());
        if (malformed != null) {
            PARAM_INVALID.show("归属列登记不合法：" + malformed);
        }
        String tenantColumn = entity.getTenantColumn();
        if (tenantColumn != null && !tenantColumn.isBlank() && !PermissionRuleSupport.isPath(tenantColumn.trim())) {
            PARAM_INVALID.show("租户列不是合法的属性名或关联路径：" + tenantColumn);
        }
        if (entity.getTableName() == null || entity.getTableName().isBlank()) {
            // 表名是"表名 → 实体形状"的唯一索引键：为空时所有维度的判定都会退化成「实体未映射」，
            // 而运行期的表现是"条件绑不上"——一个空字段能伪装成六种不同的问题，宁可在保存时就报错
            PARAM_INVALID.show("表名不能为空：维度可绑定性盘点与运行期都按表名找实体");
        }
    }
}
package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionDiffRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataPermissionOrmParityRepository;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.rule.OwnerColumnRegistration;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleSupport;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 影子比对差异表（放开前置检查要查它）。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入：这张表在某些环境（最小化测试库）可能不存在，
     * 此时<em>必须报"拿不到证据"</em>而不是"没有差异" —— 后者会伪装成"影子跑过且干净"，
     * 是放开判断里最贵的一种错。</p>
     */
    private final ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider;

    /**
     * 影子对账的 Mapper 侧仓库（同样是"读不到就报无证据"的形状，见 {@link #diffRepositoryProvider}）
     */
    private final ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider;

    /**
     * 对账探针清单：回答"这个资源进没进换轨范围"。<b>它不参与任何判定</b>，只决定 ORM_PARITY
     * 那一档是"不适用"还是"缺证据"
     */
    private final ObjectProvider<RowParityRegistry> parityRegistryProvider;

    public DataResourceService(DataPermissionEventPublisher eventPublisher,
                               DataFieldPolicyProvider fieldPolicyProvider,
                               DataPolicyService rowPolicyService,
                               EntityManagerFactory entityManagerFactory,
                               DataPermissionProperties properties,
                               ApplicationContext applicationContext,
                               ObjectProvider<DataPermissionDiffRepository> diffRepositoryProvider,
                               ObjectProvider<DataPermissionOrmParityRepository> parityRepositoryProvider,
                               ObjectProvider<RowParityRegistry> parityRegistryProvider) {
        this.eventPublisher = eventPublisher;
        this.fieldPolicyProvider = fieldPolicyProvider;
        this.rowPolicyService = rowPolicyService;
        this.entityManagerFactory = entityManagerFactory;
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.diffRepositoryProvider = diffRepositoryProvider;
        this.parityRepositoryProvider = parityRepositoryProvider;
        this.parityRegistryProvider = parityRegistryProvider;
    }

    /**
     * 注册表可能整块不存在（{@code mapper-read} 关着），因此每次问一遍而不是在构造期抓住
     *
     * @return 探针注册表；没有返回 null
     */
    private RowParityRegistry parityRegistry() {
        return parityRegistryProvider == null ? null : parityRegistryProvider.getIfAvailable();
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

        RowPolicyCounts counts = countRowPolicies(resource);
        long query = counts.query();
        long write = counts.write();
        long export = counts.export();
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
     * 放开前置检查里的<em>一项</em>
     *
     * @param code     稳定标识（前端与门禁认它，不认文案）
     * @param title    人话标题
     * @param passed   是否通过
     * @param blocking 未通过时是否阻塞放开（{@code false} = 只是提醒，不影响 ready）
     * @param detail   结论说明（含下一步该做什么）
     */
    public record ReadinessCheck(String code,
                                 String title,
                                 boolean passed,
                                 boolean blocking,
                                 String detail) {
    }

    /**
     * 一个资源的"现在能不能进 enforce 名单"结论
     *
     * @param resourceKey   资源标识
     * @param effectiveMode 该资源此刻的生效模式
     * @param ready         所有阻塞项都通过
     * @param blockers      未通过的阻塞项标题（前端直接显示，不再判一遍）
     * @param checks        全部检查项（含提醒项）
     */
    public record Readiness(String resourceKey,
                            String effectiveMode,
                            boolean ready,
                            List<String> blockers,
                            List<ReadinessCheck> checks) {
    }

    /**
     * 判据的输入：全部由调用方数好，{@link #readinessChecks} 本身<em>不查库</em>
     *
     * <p>这么切只有一个理由：判据必须能脱离 Spring 单测（与 {@link #runtimeNotes} 同一套办法）。
     * 「取数」是易变的部分，「怎么判」才是需要被钉住的部分。</p>
     *
     * @param resource                 资源行（为 null 时按"未登记"处理）
     * @param mode                     该资源此刻的生效模式
     * @param queryPolicyCount         启用中的 QUERY 行级策略数
     * @param writePolicyCount         启用中的 UPDATE + DELETE 行级策略数
     * @param bindingRiskCount         维度可绑定性风险条数
     * @param exportGap                该资源是否存在"配了遮蔽、没有导出明文授权"的缺口
     * @param shadowTableAvailable     差异表是否可读
     * @param shadowAnomalies          差异表里 ANOMALY + ERROR 的条数
     * @param shadowFilteredSamples    差异表里 EXPECTED_FILTER 的条数（证明防护真的生效过）
     * @param parityRequired           该资源是否已进入"两套 ORM 并存"的范围（登记了 Mapper 探针）
     * @param parityTableAvailable     对账表是否可读
     * @param parityLooseOrError       对账表里 MAPPER_LOOSE + ERROR 的条数（多放行 = 泄露）
     * @param paritySamples            对账表里 CONSISTENT + MAPPER_STRICT 的条数（证明对账真的跑过）
     */
    public record ReadinessInput(DataResourceEntity resource,
                                 DataPermissionProperties.Mode mode,
                                 long queryPolicyCount,
                                 long writePolicyCount,
                                 long bindingRiskCount,
                                 boolean exportGap,
                                 boolean shadowTableAvailable,
                                 long shadowAnomalies,
                                 long shadowFilteredSamples,
                                 boolean parityRequired,
                                 boolean parityTableAvailable,
                                 long parityLooseOrError,
                                 long paritySamples) {

        /**
         * 还没进入换轨范围的老形状：对账"不适用"
         *
         * <p>为什么给便捷构造器而不是让每个调用方补四个 {@code false}/{@code 0L}：{@code parityRequired=false}
         * 是唯一正确的默认（没登记探针就没有对账义务），而把 {@code true,false,0,0} 写成默认会把
         * "没跑过对账"读成"要跑却没跑"，凭空给 23 个未换轨资源各加一条阻塞项。</p>
         */
        public ReadinessInput(DataResourceEntity resource,
                              DataPermissionProperties.Mode mode,
                              long queryPolicyCount,
                              long writePolicyCount,
                              long bindingRiskCount,
                              boolean exportGap,
                              boolean shadowTableAvailable,
                              long shadowAnomalies,
                              long shadowFilteredSamples) {
            this(resource, mode, queryPolicyCount, writePolicyCount, bindingRiskCount, exportGap,
                    shadowTableAvailable, shadowAnomalies, shadowFilteredSamples, false, false, 0L, 0L);
        }
    }

    /**
     * 一个资源启用中的行级策略按动作数好的计数
     *
     * @param query  QUERY 条数
     * @param write  UPDATE + DELETE 条数
     * @param export EXPORT 条数
     */
    record RowPolicyCounts(long query, long write, long export) {
    }

    /**
     * 按动作数一遍启用中的行级策略（灰度面板与放开前置检查共用这一份数法）
     *
     * <p>两处都在数「QUERY 几条、UPDATE+DELETE 几条」，而放开检查的结论完全建立在这两个数上。
     * 抄第二遍的风险不是当场算错，是两处的口径慢慢分叉 —— 例如一处顺手把 EXPORT 算进 write、
     * 另一处没算，于是面板与检查对同一个资源给出相反的说法，而这两样东西是同一个动作的两只眼。</p>
     *
     * @param resource 资源行
     * @return 三档计数（资源未落库时全为 0）
     */
    private RowPolicyCounts countRowPolicies(DataResourceEntity resource) {
        long query = 0L;
        long write = 0L;
        long export = 0L;
        if (resource.getId() != null) {
            for (DataPolicyEntity policy : rowPolicyService.getEnabledPoliciesByResource(resource.getId())) {
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
        }
        return new RowPolicyCounts(query, write, export);
    }

    /**
     * 「能不能放开」的判据（静态，不查库）
     *
     * <p>为什么要有这个端点：{@code enforce-resources} 名单是把双刃剑 —— 少配一项会让防护形同虚设，
     * 多配一项会让保存报错或列表查空。此前"该资源准备好了没有"只能靠人对着策略表数，
     * 而数错的代价发生在<em>放开之后</em>。这里把六件事一次判完，并且刻意用两条比直觉更严的规矩：</p>
     *
     * <ul>
     *   <li><b>影子证据必须"有 EXPECTED_FILTER 样本且无异常"</b>。只看"差异为 0"是错的：
     *       一行差异都没有也可能意味着这个主体压根没被过滤（策略没命中），那正是放开后
     *       "以为有防护"的状态；</li>
     *   <li><b>拿不到差异表 = 不通过</b>，而不是当作"没有差异" —— 后者会把"没证据"伪装成"已验证"。</li>
     * </ul>
     *
     * @param in         输入
     * @param properties 灰度配置
     * @return 检查项（顺序固定，便于对比与显示）
     */
    static List<ReadinessCheck> readinessChecks(ReadinessInput in, DataPermissionProperties properties) {
        List<ReadinessCheck> checks = new ArrayList<>();
        // runtimeNotes 直接读 status，这里先补一个「未登记」的替身，免得判据方法变成 NPE 现场
        DataResourceEntity resource = in.resource() == null
                ? new DataResourceEntity().setStatus("MISSING") : in.resource();
        boolean enabled = "ENABLED".equals(resource.getStatus());
        checks.add(new ReadinessCheck("REGISTERED_ENABLED", "资源已登记且启用", enabled, true,
                enabled ? "status=ENABLED"
                        : "资源未登记或已停用：读写两侧都会把它当未登记处理"));

        checks.add(new ReadinessCheck("DIMENSIONS_BINDABLE", "已用维度都能绑到列", in.bindingRiskCount() == 0L, true,
                in.bindingRiskCount() == 0L ? "无绑定风险"
                        : "仍有 " + in.bindingRiskCount() + " 条维度绑不上列：enforce 下这些条件恒假，"
                                + "该主体一行也查不到（先在「维度可绑定性」面板清零）"));

        boolean queryOk = in.queryPolicyCount() > 0L;
        checks.add(new ReadinessCheck("QUERY_POLICY", "查询侧至少有一条启用的策略", queryOk, true,
                queryOk ? in.queryPolicyCount() + " 条 QUERY 策略"
                        : "没有任何 QUERY 策略：放开后这一档不会过滤任何东西，「已 enforce」会变成假绿灯"));

        boolean writeOk = in.writePolicyCount() > 0L;
        checks.add(new ReadinessCheck("WRITE_POLICY", "更新/删除有策略", writeOk, true,
                writeOk ? in.writePolicyCount() + " 条 UPDATE/DELETE 策略"
                        : "没有 UPDATE/DELETE 策略：进名单后 DataPermissionGuard 会把该资源的保存与删除直接拒掉"));

        checks.add(new ReadinessCheck("EXPORT_GRANT", "导出遮蔽列已配明文授权", !in.exportGap(), false,
                in.exportGap() ? "存在「配了遮蔽、没有 EXPORT 明文授权」的列：受限角色一旦点导出会整份被拒"
                        + "（这是 fail-closed 的预期行为，不阻塞放开；要放行某角色就补 actionName=EXPORT 策略）"
                        : "无导出授权缺口"));

        boolean shadowOk;
        String shadowDetail;
        if (!in.shadowTableAvailable()) {
            shadowOk = false;
            shadowDetail = "读不到影子差异表（未建表或数据源不可用）：无法证明影子比对跑过，不能视为「没有差异」";
        } else if (in.shadowAnomalies() > 0L) {
            shadowOk = false;
            shadowDetail = "有 " + in.shadowAnomalies() + " 条 ANOMALY/ERROR 差异：先逐条解释或修好，再放开";
        } else if (in.shadowFilteredSamples() == 0L) {
            shadowOk = false;
            shadowDetail = "还没有一条 EXPECTED_FILTER 样本：说明"
                    + "「加条件后会少行」这件事从未被证明过（也可能是该主体本来就没被过滤）";
        } else {
            shadowOk = true;
            shadowDetail = in.shadowFilteredSamples() + " 条 EXPECTED_FILTER 样本，0 条异常";
        }
        checks.add(new ReadinessCheck("SHADOW_EVIDENCE", "影子比对已有正向样本且无异常", shadowOk, true, shadowDetail));

        // ORM 对账：只在"这个资源真的开始用 Mapper 读数"之后才是阻塞项。
        // 对未换轨的资源报红没有意义（它压根没有第二个渲染方向），而把"不适用"报成"通过"更糟 ——
        // 面板上会出现一片绿，读的人以为对账做过。所以这里三档：不适用 / 缺证据（红）/ 有证据（绿）。
        if (!in.parityRequired()) {
            checks.add(new ReadinessCheck("ORM_PARITY", "两套 ORM 行数一致", true, false,
                    "该资源未登记 Mapper 行数探针（kubo.data-permission.mapper-read 之外还要在"
                            + " RowParityCounter 上出现），本轮不适用；换轨前先按 §19.36 补探针"));
        } else if (!in.parityTableAvailable()) {
            checks.add(new ReadinessCheck("ORM_PARITY", "两套 ORM 行数一致", false, true,
                    "读不到对账表（未建表或数据源不可用）：无法证明 Mapper 侧数得出同一批行，"
                            + "不能视为「一致」—— 与影子那条同一口径，缺证据不等于干净"));
        } else if (in.parityLooseOrError() > 0L) {
            checks.add(new ReadinessCheck("ORM_PARITY", "两套 ORM 行数一致", false, true,
                    "有 " + in.parityLooseOrError() + " 条 MAPPER_LOOSE/ERROR：Mapper 侧比 JPA 侧多放行行"
                            + "（或对账本身跑失败）。多放行就是泄露，先归因再放开"));
        } else if (in.paritySamples() == 0L) {
            checks.add(new ReadinessCheck("ORM_PARITY", "两套 ORM 行数一致", false, true,
                    "一条对账样本都没有：探针登记了、影子也跑了，却没数出过可比的两行 —— "
                            + "大概率是采样没命中或片段根本没进 SQL"));
        } else {
            checks.add(new ReadinessCheck("ORM_PARITY", "两套 ORM 行数一致", true, true,
                    in.paritySamples() + " 条可比样本，0 条 MAPPER_LOOSE/ERROR；MAPPER_STRICT（Mapper 少放行）"
                            + "不阻塞放开，但它是「有人少看了数据」的线索，detail 要能解释"));
        }

        List<String> notes = runtimeNotes(resource, in.mode(), in.queryPolicyCount(), in.writePolicyCount(),
                in.bindingRiskCount(), properties);
        checks.add(new ReadinessCheck("CONFIG_NOTES", "配置与策略数量之间没有已知冲突", notes.isEmpty(), true,
                notes.isEmpty() ? "无" : String.join("；", notes)));
        return checks;
    }

    /**
     * 是否可放开：所有阻塞项都通过
     *
     * @param checks 检查项
     * @return 可以放开返回 true
     */
    static boolean readyOf(List<ReadinessCheck> checks) {
        if (checks == null || checks.isEmpty()) {
            return false;
        }
        return checks.stream().noneMatch(c -> c.blocking() && !c.passed());
    }

    /**
     * 未通过的阻塞项标题
     *
     * @param checks 检查项
     * @return 阻塞原因列表（可能为空）
     */
    static List<String> blockersOf(List<ReadinessCheck> checks) {
        List<String> blockers = new ArrayList<>();
        for (ReadinessCheck check : checks) {
            if (check.blocking() && !check.passed()) {
                blockers.add(check.title() + "：" + check.detail());
            }
        }
        return blockers;
    }

    /**
     * 单个资源的放开前置检查（<b>只报数，不拦阻</b>：放开与否仍是配置动作）
     *
     * @param resourceKey 资源标识；为 null / 未登记时返回一份"全红"结果而不是抛异常
     * @return 结论
     */
    public Readiness readiness(String resourceKey) {
        DataResourceEntity resource = resourceKey == null ? null : repository.getByResourceKey(resourceKey);
        if (resource == null) {
            DataResourceEntity ghost = new DataResourceEntity().setResourceKey(resourceKey).setStatus("MISSING");
            List<ReadinessCheck> checks = readinessChecks(new ReadinessInput(ghost,
                    properties.effectiveMode(resourceKey), 0L, 0L, 0L, false, false, 0L, 0L,
                    // 资源本身不存在，谈不上"已换轨"；但探针登记了它也得暴露出来（有人先写了 Mapper 再补登记）
                    parityRegistry() != null && parityRegistry().resources().contains(resourceKey),
                    false, 0L, 0L), properties);
            return new Readiness(resourceKey, properties.effectiveMode(resourceKey).name(), false,
                    blockersOf(checks), checks);
        }
        return readinessOf(resource);
    }

    /**
     * 全部已登记资源的放开前置检查（面板用；未登记的服务在 {@link #runtimeStatus()} 的另一张清单里）
     *
     * @return 按资源登记顺序
     */
    public List<Readiness> readinessAll() {
        List<Readiness> result = new ArrayList<>();
        List<DataResourceEntity> all = repository.findAll();
        if (all == null) {
            return result;
        }
        Map<String, Long> riskCounts = new LinkedHashMap<>();
        for (DimensionBindingRiskItem risk : dimensionBindingRisks()) {
            riskCounts.merge(risk.resourceKey(), 1L, Long::sum);
        }
        Set<String> gapKeys = new LinkedHashSet<>();
        for (ExportGapItem gap : exportGaps()) {
            gapKeys.add(gap.resourceKey());
        }
        for (DataResourceEntity resource : all) {
            if (resource == null || resource.getResourceKey() == null) {
                continue;
            }
            result.add(readinessOf(resource, riskCounts.getOrDefault(resource.getResourceKey(), 0L),
                    gapKeys.contains(resource.getResourceKey())));
        }
        return result;
    }

    /**
     * 组装一个资源的检查（取数在这里，判据在 {@link #readinessChecks}）
     */
    private Readiness readinessOf(DataResourceEntity resource) {
        return readinessOf(resource,
                dimensionBindingRisks().stream()
                        .filter(item -> resource.getResourceKey().equals(item.resourceKey())).count(),
                exportGaps().stream().anyMatch(item -> resource.getResourceKey().equals(item.resourceKey())));
    }

    /**
     * 同上，但由调用方把两份盘点结果传进来（{@code readinessAll()} 复用同一次遍历）
     */
    private Readiness readinessOf(DataResourceEntity resource, long bindingRiskCount, boolean exportGap) {
        String resourceKey = resource.getResourceKey();
        RowPolicyCounts counts = countRowPolicies(resource);
        long query = counts.query();
        long write = counts.write();
        long anomalies = 0L;
        long filtered = 0L;
        boolean tableAvailable = false;
        DataPermissionDiffRepository diffRepository = diffRepositoryProvider == null
                ? null : diffRepositoryProvider.getIfAvailable();
        if (diffRepository != null && resourceKey != null) {
            try {
                anomalies = diffRepository.countByResourceKeyAndVerdict(resourceKey, DataPermissionDiffEntity.VERDICT_ANOMALY)
                        + diffRepository.countByResourceKeyAndVerdict(resourceKey, DataPermissionDiffEntity.VERDICT_ERROR);
                filtered = diffRepository.countByResourceKeyAndVerdict(resourceKey, DataPermissionDiffEntity.VERDICT_EXPECTED_FILTER);
                tableAvailable = true;
            } catch (RuntimeException e) {
                log.warn("放开前置检查读不到影子差异表，按「无证据」处理: {}", e.getMessage());
            }
        }
        boolean parityRequired = false;
        boolean parityTableAvailable = false;
        long parityLooseOrError = 0L;
        long paritySamples = 0L;
        RowParityRegistry registry = parityRegistry();
        if (registry != null && resourceKey != null && registry.counterFor(resourceKey) != null) {
            parityRequired = true;
            DataPermissionOrmParityRepository parityRepository = parityRepositoryProvider == null
                    ? null : parityRepositoryProvider.getIfAvailable();
            if (parityRepository != null) {
                try {
                    // 四个数分档，不用 verdict <> 'CONSISTENT' 一把捞：捞出来的"异常"里混着可解释的
                    // MAPPER_STRICT，那样放开检查就会长期红着一个不必修的东西，而大家学会忽略红
                    parityLooseOrError = parityRepository.countByResourceKeyAndVerdict(resourceKey,
                                    DataPermissionOrmParityEntity.VERDICT_MAPPER_LOOSER)
                            + parityRepository.countByResourceKeyAndVerdict(resourceKey,
                                    DataPermissionOrmParityEntity.VERDICT_ERROR);
                    paritySamples = parityRepository.countByResourceKeyAndVerdict(resourceKey,
                                    DataPermissionOrmParityEntity.VERDICT_CONSISTENT)
                            + parityRepository.countByResourceKeyAndVerdict(resourceKey,
                                    DataPermissionOrmParityEntity.VERDICT_MAPPER_STRICTER);
                    parityTableAvailable = true;
                }
                catch (RuntimeException e) {
                    log.warn("放开前置检查读不到 ORM 对账表，按「无证据」处理: {}", e.getMessage());
                }
            }
        }
        List<ReadinessCheck> checks = readinessChecks(new ReadinessInput(resource, properties.effectiveMode(resourceKey),
                query, write, bindingRiskCount, exportGap, tableAvailable, anomalies, filtered,
                parityRequired, parityTableAvailable, parityLooseOrError, paritySamples), properties);
        return new Readiness(resourceKey, properties.effectiveMode(resourceKey).name(), readyOf(checks),
                blockersOf(checks), checks);
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
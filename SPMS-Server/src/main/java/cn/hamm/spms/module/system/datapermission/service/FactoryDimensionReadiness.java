package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.rule.OwnerColumnRegistration;
import cn.hamm.spms.framework.curd.base.EntityClassIndex;
import cn.hamm.spms.framework.curd.base.EntityMetadata;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>工厂（FACTORY）维度就绪度校验（决策 D13）</h1>
 *
 * <p>{@code kubo.data-permission.factory-dimension-enabled} 的历史问题是<b>它只是一句承诺</b>：
 * 打开它，{@link DimensionGuard} 就放行 {@code FACTORY} 策略，但链路的另外两环根本不存在 ——
 * {@code PermissionContext.factoryIds()} 在 {@code RequestInterceptor} 里被硬编码成 {@code Set.of()}，
 * 而业务实体上也没有工厂归属列。于是开关打开后的真实行为是：</p>
 * <ul>
 *   <li>{@code factoryIds} 恒空 ⇒ 策略谁都匹配不上 ⇒ 列表退化成"无策略"分支（配了等于没配，<b>假安全</b>）；</li>
 *   <li>条件树里引用 {@code factoryIds} ⇒ 渲染成恒假 ⇒ 全员空结果（<b>自锁</b>）。</li>
 * </ul>
 * <p>这两种都比"功能缺失"糟：它们看起来是"权限生效了"。</p>
 *
 * <p><b>本类的立场</b>：开关不许靠人回忆前置条件。启动期用<b>已存在的元数据</b>核对两件事 ——
 * ①用户与工厂的关系在 JPA 侧有承载（否则 {@code factoryIds} 无从解析）；
 * ②每个已注册资源的实体类真的有工厂归属列（否则行权限只能 {@code deny()}，全员空结果）。
 * 任一不满足 ⇒ <b>启动失败</b>并列出缺哪一条。之所以在 {@code ApplicationReadyEvent} 抛而不是只打日志：
 * "配置错了就打不开"是唯一能被运维读到的信号，日志会被刷掉，而默认放行会造成上面两种假象。</p>
 *
 * <p><b>为什么用元模型而不是查 {@code information_schema}</b>：表存在不代表代码能用到它，
 * 反之代码有映射则表必然由 {@code ddl-auto} 或迁移脚本给出；且查库要背方言差异
 * （MySQL 的 {@code TABLE_SCHEMA = DATABASE()} 与 H2 的 {@code PUBLIC} 不一样），
 * 那会让这条校验在自己的测试里假绿。就绪度判的是"链路是否接上"，代码侧元数据才是这件事的真相。</p>
 *
 * @author Hamm.cn
 */
@Component
public class FactoryDimensionReadiness {

    /**
     * 用户实体上可承载"用户 ↔ 工厂"关系的字段名候选。
     *
     * <p>刻意不写死一个名字：仓库里同类关系（{@code storageList} / {@code departmentList}）用的是
     * {@code XxxList} 约定，但工厂那张表还没建，先接受几种自然写法，避免将来为了过门禁去改字段名。</p>
     */
    static final Set<String> USER_RELATION_FIELDS = Set.of("factoryList", "factories", "factoryIds", "userFactoryList");

    /**
     * 资源实体上的工厂归属字段名候选（{@code factory} 指 {@code @ManyToOne} 关联，
     * 它在库里同样产出 {@code factory_id} 列，因此算已具备）
     */
    static final Set<String> OWNER_FIELD_NAMES = Set.of("factoryId", "factory");

    /**
     * 物理列名（{@code @TableField("factory_id")} 或资源登记的归属列）
     */
    static final String OWNER_COLUMN_NAME = "factory_id";

    private final DataPermissionProperties properties;

    private final DataResourceRepository resourceRepository;

    /**
     * 实体类索引（J3 起来自 {@code @TableName} 扫描）
     */
    private final EntityClassIndex entityClassIndex;

    public FactoryDimensionReadiness(
            DataPermissionProperties properties,
            DataResourceRepository resourceRepository,
            EntityClassIndex entityClassIndex
    ) {
        this.properties = properties;
        this.resourceRepository = resourceRepository;
        this.entityClassIndex = entityClassIndex;
    }

    /**
     * 启动期核对：开关关着就什么都不做（默认态必须零成本、零噪音）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verifyOnStartup() {
        if (properties == null || !properties.isFactoryDimensionEnabled()) {
            return;
        }
        // 索引缺席（单测直接 new 出来、只验开关短路）时按"信息不足 ⇒ 不就绪"处理
        List<String> problems = inspect(
                entityClassIndex == null ? List.of() : entityClassIndex.classes(), registeredResources());
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "kubo.data-permission.factory-dimension-enabled=true，但工厂维度并未真正落地（D13 前置条件未满足）：\n  - "
                            + String.join("\n  - ", problems)
                            + "\n要么按施工图补齐这些环节，要么把开关关掉；"
                            + "开着开关却没有链路 = 策略永不命中（假安全）或条件树恒假（全员空结果）。"
            );
        }
    }

    /**
     * 纯反射的就绪度判定（不碰 Spring、不查库，因此可被单测直接喂假类）
     *
     * @param entities  实体类集合（J3：来自 {@code @TableName} 扫描，不再是 JPA 元模型）
     * @param resources 已登记的数据资源
     * @return 问题清单；空表示三条前置条件里可静态判定的部分都成立
     */
    static List<String> inspect(Collection<Class<?>> entities, Collection<DataResourceEntity> resources) {
        List<String> problems = new ArrayList<>();

        Map<String, Class<?>> byTable = EntityMetadata.byTable(entities);

        // ① 用户 ↔ 工厂：没有承载字段，factoryIds 就只能继续硬编码空集合
        boolean userCarriesRelation = byTable.values().stream()
                .filter(type -> type.getSimpleName().endsWith("UserEntity"))
                .anyMatch(type -> hasAnyField(type, USER_RELATION_FIELDS));
        if (!userCarriesRelation) {
            problems.add("UserEntity 上没有用户 ↔ 工厂的关系字段（候选："
                    + String.join(" / ", new LinkedHashSet<>(USER_RELATION_FIELDS))
                    + "），PermissionContext.factoryIds() 无从解析，仍是恒空集合");
        }

        // ② 至少有一个已注册资源真的具备工厂归属列。
        //    逐资源"没列就拒启动"是过头的（24 个资源里只有部分接工厂是正常形态），
        //    但"一个都没有"意味着开关打开后全链路无人可命中 —— 那必须拦。
        //    单个资源缺列时读侧已有安全网：FACTORY 策略本该产生条件却绑不上列 ⇒ enforce 恒假 +
        //    binding 记账（DataPermissionSpecification#apply 的 expectedConditions 分支），
        //    不会静默放开，因此不需要在启动期重复设卡。
        List<String> withoutColumn = new ArrayList<>();
        int ready = 0;
        boolean sawResource = false;
        for (DataResourceEntity resource : resources) {
            String tableName = resource.getTableName();
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            sawResource = true;
            Class<?> entity = byTable.get(normalize(tableName));
            if (entity == null) {
                withoutColumn.add(resource.getResourceKey() + "（表 " + tableName + " 没有对应的 @TableName 实体类）");
                continue;
            }
            if (hasFactoryColumn(entity, resource)) {
                ready++;
            }
            else {
                withoutColumn.add(resource.getResourceKey() + "（" + entity.getSimpleName() + "）");
            }
        }
        if (sawResource && ready == 0) {
            problems.add("没有任何已注册资源具备工厂归属列（字段 " + String.join(" / ", OWNER_FIELD_NAMES)
                    + " 或列 " + OWNER_COLUMN_NAME + "，或在 owner_columns 登记），FACTORY 策略一条都绑不上；缺列的资源："
                    + (withoutColumn.isEmpty() ? "（无）" : String.join("、", withoutColumn)));
        }
        return problems;
    }

    private static boolean hasFactoryColumn(Class<?> entity, DataResourceEntity resource) {
        if (hasAnyField(entity, OWNER_FIELD_NAMES)) {
            return true;
        }
        for (Field field : fieldsOf(entity)) {
            if (EntityMetadata.isColumn(field)
                    && OWNER_COLUMN_NAME.equalsIgnoreCase(EntityMetadata.columnName(field))) {
                return true;
            }
        }
        // 归属列允许由资源显式登记（与条件树白名单同源）。分两种写法：
        String ownerColumns = resource.getOwnerColumns();
        if (ownerColumns != null) {
            // ① 带 FACTORY: 维度前缀的登记（D13-B）：`FACTORY:storage.id` 这类关联路径
            //    必须能在实体形状上真绑上才算具备 —— 光有登记没绑上，是又一条「配了没用」，
            //    打开开关后该资源上的 FACTORY 策略照样一条都拼不出来。
            List<OwnerColumnRegistration> items = OwnerColumnRegistration.parse(ownerColumns);
            List<String> factoryPaths = OwnerColumnRegistration.pathsFor(items, "FACTORY");
            if (!factoryPaths.isEmpty()) {
                EntityShapeIndex.Shape shape = EntityShapeIndex.shapeOf(entity);
                return factoryPaths.stream().anyMatch(shape::binds);
            }
            // ② 裸写法（无维度前缀）：沿用「登记了 factory_id 就算具备」的既有口径，
            //    不动历史 owner_columns 的兼容语义（FactoryDimensionReadinessTest#ownerColumnsCountAsReady）
            for (String part : ownerColumns.split(",")) {
                if (normalize(part).equals(OWNER_COLUMN_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnyField(Class<?> type, Set<String> names) {
        for (Field field : fieldsOf(type)) {
            if (names.contains(field.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 含父类的字段清单：{@code BaseEntity} 之类的继承字段也算数（映射是继承来的，不该被漏掉）
     */
    /**
     * 字段收集与表名归一化都委托 {@link EntityShapeIndex}：
     * 「什么算同一个字段」只能有一份规则，否则就绪度与盘点会各说各话
     */
    private static List<Field> fieldsOf(Class<?> type) {
        return EntityShapeIndex.fieldsOf(type);
    }

    private static String normalize(String raw) {
        return EntityShapeIndex.normalizeTableName(raw);
    }

    private Collection<DataResourceEntity> registeredResources() {
        if (resourceRepository == null) {
            return List.of();
        }
        List<DataResourceEntity> all = resourceRepository.selectList(null);
        return all == null ? List.of() : all;
    }
}

package cn.hamm.spms.base;

import cn.hamm.spms.framework.web.annotation.FilterIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>实体字段初始值不得成为隐形查询条件</h1>
 *
 * <h2>这个测试守的是一条会静默清空整个页面的规则</h2>
 *
 * <p>{@code QueryListRequest<T>} 的 {@code filter} 就是实体 {@code T} 本身。前端发
 * {@code {"filter":{}}} 时，Jackson 会 new 一个实体出来 —— <b>字段初始化器照常执行</b>，
 * 于是「调用方什么都没传」和「调用方传了这个默认值」在反射里完全无法区分。
 * {@link BaseService#buildPredicates} 只跳过 {@code null}，所以任何
 * <b>非 null 的字段初始值都会变成一条 WHERE</b>。</p>
 *
 * <p>两次真实事故都出在这里：</p>
 * <ul>
 *   <li><b>菜单事故</b>：{@code BaseEntity.isPublished = false} 让所有默认列表查询变成
 *       {@code WHERE is_published = 0}，9 个后台页面从侧边栏消失（911 个后端测试 + 778 个前端测试全绿，
 *       浏览器 console 干净）。修法是给该字段加 {@code @FilterIgnore} + V14 把菜单组的
 *       {@code is_published} 归零。</li>
 *   <li><b>数据权限治理三页全空</b>：{@code DataResourceEntity.status = "DRAFT"} 而库里 17 行全是
 *       {@code ENABLED} ⇒ {@code /dataResource/getList} 恒回 0 行；{@code DataPolicyEntity} 一次踩四个
 *       （conditionJson/priority/enabled/versionNo）⇒ 65 行策略一行不出；{@code DataFieldPolicyEntity}
 *       踩三个 ⇒ 4 行列级策略一行不出。表头照常渲染，看起来就像「还没配置」，而不是「查不出来」。</li>
 * </ul>
 *
 * <p>值得注意的是，第二个坑在本模块<b>已经被记过两次</b>——
 * {@code DataResourceService#getByResourceKey} 与
 * {@code DataPolicyRepository#existsByResourceIdAndActionNameAndSubjectType} 的注释都写着
 * 「用实体当查询条件会把字段默认值一并拼进 WHERE」，但当时的处置是<b>绕过</b>
 * （另写一个 repository 方法），源头没堵，HTTP 列表路径于是继续中招。本测试就是把源头堵上：
 * 新加一个带初始值的实体字段而忘了 {@code @FilterIgnore}，CI 直接红。</p>
 *
 * <h2>判定口径必须与 buildPredicates 逐条对齐</h2>
 *
 * <p>下面的跳过规则不是随手写的，它照抄 {@code BaseService#buildPredicates} 的跳过规则：
 * static / {@code @Transient} / {@code @OneToMany} / {@code @ManyToMany} / 集合与 Map 类型都不参与
 * filter 拼装，所以它们的初始值无害；其余字段只要新实例上取到非 null，就是一条隐形 WHERE。
 * 口径若与实现漂移，这个测试就会开始报假阳性（或更糟：漏报）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("实体字段初始值不得成为隐形查询条件（filter 就是实体本身）")
class EntityFilterDefaultTest {

    /**
     * 与 {@code PermissionScanner.scanPermission(Application.class.getPackageName())} 同一个根包。
     */
    private static final String ROOT_PACKAGE = "cn.hamm.spms";

    /**
     * 白名单：<b>刻意保留</b>的隐形条件，键为 {@code 声明类简单名#字段名}（继承来的字段归声明它的那个类，
     * 不是被扫描的具体实体），值必须写清理由。
     *
     * <p>只有一项。加第二项之前请先想清楚：能不能改成 {@code @FilterIgnore} +
     * 在真正需要按该字段筛选的地方显式传条件？「默认值恰好就是我想要的筛选条件」
     * 是一种巧合，不是设计 —— 数据一变（例如某天出现 {@code version_no = 2} 的行）它就翻车，
     * 而且翻车的样子是「列表悄悄少了几行」，没有人会报错。</p>
     */
    private static final Map<String, String> ALLOWED_DEFAULT_FILTERS = Map.of(
            "BaseEntity#isDisabled",
            "产品语义：列表默认只出「未停用」的行。停用是用户主动把一条数据下线，默认隐藏它符合直觉；"
                    + "这与 isPublished 不同 —— 后者的默认 false 会让「已发布」的数据从列表消失（菜单事故的成因），"
                    + "所以 isPublished 标了 @FilterIgnore，isDisabled 刻意不标。"
    );

    @Test
    @DisplayName("扫描到的每个实体：非 null 字段初始值必须要么标 @FilterIgnore，要么在白名单里写明理由")
    void everyNonNullFieldDefaultMustBeFilterIgnoredOrWhitelisted() throws Exception {
        Set<Class<?>> entities = scanEntities();
        // 扫不到实体 = 扫描器坏了。一个「什么都没检查」的守卫比没有守卫更危险，所以先断言扫描量。
        assertTrue(entities.size() >= 40,
                "只扫到 " + entities.size() + " 个 @Entity，扫描器可能坏了（本项目实体数远大于此）");
        assertTrue(entities.stream().anyMatch(c -> c.getSimpleName().equals("MaterialEntity")),
                "扫描漏掉了 MaterialEntity：include filter 或根包写错了");

        Map<String, String> violations = new TreeMap<>();
        Set<String> covered = new TreeSet<>();
        for (Class<?> entity : entities) {
            // 生产实体都是公有顶层类：混进嵌套类/测试探针说明 isProductionClass 的判据失效了
            assertTrue(!entity.getSimpleName().contains("$") && !entity.getSimpleName().endsWith("Test"),
                    "扫描收进了非生产实体 " + entity.getName() + "：isProductionClass 的判据失效了");
            Object fresh = newInstance(entity);
            for (Field field : filterMappedFields(entity)) {
                field.setAccessible(true);
                if (field.get(fresh) == null) {
                    // buildPredicates 跳过 null：没写初始值的字段不会成为条件，无需处理
                    continue;
                }
                // 键用「声明类」而不是「被扫描的实体」：isDisabled 声明在 BaseEntity 上，
                // 用具体实体做键会让同一个声明散成 55 条，白名单要写 55 遍才生效，
                // 而且真正该改的那一行（BaseEntity.java:79）在报错信息里根本看不出来。
                String key = field.getDeclaringClass().getSimpleName() + "#" + field.getName();
                if (field.isAnnotationPresent(FilterIgnore.class) || ALLOWED_DEFAULT_FILTERS.containsKey(key)) {
                    covered.add(key);
                } else {
                    violations.put(key, "字段初始值会变成隐形 WHERE 条件，且没有 @FilterIgnore（声明处 "
                            + field.getDeclaringClass().getName() + "）");
                }
            }
        }

        // 白名单不许腐烂：写在白名单里却已经不再触发（字段改名/删除，或改标了 @FilterIgnore）的项必须清掉，
        // 否则白名单会慢慢变成一份没人看的免责清单。判据是 covered —— 它同时收「标了注解的」和
        // 「在白名单里且此刻真的带着非 null 初始值的」，所以不在 covered 里的白名单项就是死条目。
        Set<String> staleAllowlist = new TreeSet<>(ALLOWED_DEFAULT_FILTERS.keySet());
        staleAllowlist.removeAll(covered);
        assertEquals(Set.of(), staleAllowlist,
                "白名单里有已经不再触发的项（字段改名/删除，或已经加了 @FilterIgnore）：" + staleAllowlist
                        + "。请把它从 ALLOWED_DEFAULT_FILTERS 移除，白名单必须只装「此刻真的需要豁免」的字段。");

        assertEquals(Map.of(), violations, """
                下列实体字段带有非 null 初始值，会被 BaseService.buildPredicates 当成调用方传来的查询条件：
                  """ + violations + """

                后果不是报错，而是「列表悄悄少几行甚至全空」。真机实例：
                DataResourceEntity.status="DRAFT" 让 /dataResource/getList 对 17 行 ENABLED 数据恒回 0 行。

                处置二选一：
                  1) 该初始值只是 INSERT 缺省（绝大多数情况）→ 给字段加 @FilterIgnore；
                  2) 确实要把它当默认筛选条件 → 加进 ALLOWED_DEFAULT_FILTERS 并写清理由。
                不要改 buildPredicates 去「猜」调用方意图：反射看不出「没传」和「传了默认值」的区别。
                """);

        // 正向钉子：这三处是本轮真机实测出问题的字段，注解被误删时必须立刻红。
        assertTrue(covered.containsAll(Set.of(
                        "DataResourceEntity#status",
                        "DataPolicyEntity#enabled",
                        "DataFieldPolicyEntity#enabled",
                        "BaseEntity#isPublished")),
                "已修好的 @FilterIgnore 被摘掉了，实际覆盖集：" + covered);
    }

    @Test
    @DisplayName("检测器自检：未标注的初始值必须被抓到，标注过的必须放行（防止空转假绿）")
    void detectorMustFlagUnannotatedDefaultsAndPassAnnotatedOnes() {
        // 负向对照：这个探针没标 @FilterIgnore，必须被点名；点名不到就说明检测器空转
        assertEquals(List.of("TrapProbe#status", "TrapProbe#versionNo"), violationsOf(TrapProbe.class),
                "检测器没有抓到未标注的字段初始值：守卫已经空转，主用例的通过不算数");
        // 正向对照：标了 @FilterIgnore 的必须放行，否则主用例会被迫把一切都加进白名单
        assertEquals(List.of(), violationsOf(SafeProbe.class),
                "检测器把已经标注 @FilterIgnore 的字段也报成违规：主用例将无法通过");
        // 口径对照：集合 / @Transient / static / 无初始值 都不该被报出来
        assertEquals(List.of(), violationsOf(IgnoredShapesProbe.class),
                "检测器把不参与 filter 拼装的字段也报成违规（口径与 buildPredicates 漂移了）");
    }

    /**
     * 对一个类跑一遍判定，返回「有非 null 初始值且未标 {@code @FilterIgnore}」的字段键（升序）。
     */
    private static List<String> violationsOf(Class<?> clazz) {
        List<String> result = new ArrayList<>();
        Object fresh = newInstance(clazz);
        for (Field field : filterMappedFields(clazz)) {
            try {
                field.setAccessible(true);
                if (field.get(fresh) != null && !field.isAnnotationPresent(FilterIgnore.class)) {
                    result.add(field.getDeclaringClass().getSimpleName() + "#" + field.getName());
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("读不到字段 " + clazz.getSimpleName() + "#" + field.getName(), e);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * 取出「会参与 filter 拼装」的字段：沿继承链 concrete→Object，按
     * {@code BaseService#buildPredicates} 的口径剔除 static / synthetic / {@code @Transient} /
     * {@code @OneToMany} / {@code @ManyToMany} / 集合与 Map。
     */
    private static List<Field> filterMappedFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> cursor = clazz; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(Transient.class)
                        || field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToMany.class)) {
                    continue;
                }
                if (Collection.class.isAssignableFrom(field.getType())
                        || Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * 扫描全部<b>生产</b> {@code @Entity}。抽象类不进（无法实例化，也不映射表）。
     */
    private static Set<Class<?>> scanEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<Class<?>> result = new LinkedHashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ROOT_PACKAGE)) {
            Class<?> clazz;
            try {
                clazz = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                throw new IllegalStateException("扫描到无法加载的 @Entity：" + definition.getBeanClassName(), e);
            }
            if (Modifier.isAbstract(clazz.getModifiers()) || !isProductionClass(clazz)) {
                continue;
            }
            result.add(clazz);
        }
        return result;
    }

    /**
     * 只收 {@code src/main/java} 里的实体。
     *
     * <p>测试类路径上还躺着一批 {@code @Entity} 探针（例如
     * {@code DataOwnershipMigrationServiceTest$OwnedEntity}）：它们不映射表、不参与 HTTP filter，
     * 混进来只会让本守卫因为<b>别人的探针形状</b>而红 —— 那种失败与本规则要防的事故毫无关系，
     * 而守卫一旦开始因为无关原因失败，大家学到的就是「这个测试挂了先忽略」。
     * 判据取 {@code .class} 的物理位置：生产类在 {@code target/classes}，测试类在
     * {@code target/test-classes}；嵌套/局部/匿名类一律排除（生产实体都是公有顶层类）。</p>
     */
    private static boolean isProductionClass(Class<?> clazz) {
        if (clazz.isMemberClass() || clazz.isLocalClass() || clazz.isAnonymousClass()) {
            return false;
        }
        var codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return false;
        }
        String path = codeSource.getLocation().getPath().replace('\\', '/');
        return !path.contains("test-classes") && !path.contains("/test/");
    }

    /**
     * 新建一个实例来读字段初始值。构造函数一律 {@code setAccessible(true)}：
     * 本测试与实体不同包，遇到包级私有的类会直接 {@code IllegalAccessException}
     * （第一版就是这么挂的）。
     */
    private static Object newInstance(Class<?> clazz) {
        try {
            var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法实例化 " + clazz.getSimpleName() + " 做默认值检查", e);
        }
    }

    /**
     * 负向对照探针：<b>刻意不标</b> {@code @FilterIgnore}。
     *
     * <p>不标 {@code @Entity}：本测试自己扫的是 {@code @Entity}，探针若也标上就会混进主用例的
     * 违规集里；而 Hibernate 的实体扫描在测试 profile 下是 {@code ddl-auto: none}，
     * 多一个实体也不会建表 —— 但没必要去碰这个边界。</p>
     */
    static class TrapProbe {
        private String status = "DRAFT";
        private Long versionNo = 1L;
    }

    /**
     * 正向对照探针：标了 {@code @FilterIgnore}，必须放行。
     */
    static class SafeProbe {
        @FilterIgnore
        private String status = "DRAFT";
        @FilterIgnore
        private Boolean enabled = true;
    }

    /**
     * 口径对照探针：这四种形状都不参与 filter 拼装，一个都不该被报出来。
     */
    static class IgnoredShapesProbe {
        /** 无初始值：新实例上是 null，buildPredicates 直接跳过 */
        private String plain;
        /** static：buildPredicates 跳过 */
        private static final String CONSTANT = "x";
        /** @Transient：不映射列，buildPredicates 跳过 */
        @Transient
        private String notAColumn = "y";
        /** @OneToMany：buildPredicates 跳过 */
        @OneToMany
        private List<String> children = new ArrayList<>();
        /** 集合类型：buildPredicates 跳过 */
        private Set<String> tags = new TreeSet<>();
    }
}

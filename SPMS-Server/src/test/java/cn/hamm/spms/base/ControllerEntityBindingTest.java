package cn.hamm.spms.base;

import cn.hamm.spms.base.bill.BaseBillController;
import cn.hamm.spms.module.asset.contract.ContractEntity;
import cn.hamm.spms.module.asset.contract.participant.ParticipantEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>控制器身份必须等于其实体泛型</h1>
 *
 * <h2>这条不变量为什么是安全边界，而不是命名洁癖</h2>
 *
 * <p>本项目里「一个模块是谁」由三条互相独立的推导链决定，而它们全都只看类名/泛型：</p>
 * <ol>
 *   <li><b>权限身份</b>来自<em>控制器</em>：{@code PermissionScanner} 按
 *       {@code identity + "_" + method.getName()} 生成权限码，于是
 *       {@code ParticipantController.delete → Participant_delete}；</li>
 *   <li><b>数据权限资源键</b>来自<em>服务类</em>：{@code DataPermissionResourceResolver.resolve(serviceClass)}
 *       去掉 {@code Service} 后缀、取 {@code module.} 之后的包段，于是
 *       {@code ParticipantService → asset.participant}；</li>
 *   <li><b>真正读写的表</b>来自<em>实体泛型</em>：{@code BaseRepository<E>} 决定 JPA 的查询根。</li>
 * </ol>
 *
 * <p>三者只要有一个对不上，就出现「拿着 A 的权限改 B 的表」。2026-09 真机排查抓到的实例：
 * {@code ParticipantController / ParticipantService / ParticipantRepository} 三处泛型全部写成
 * {@code ContractEntity}，而 {@code ParticipantEntity} 就在同一个包里、{@code participant} 表也在
 * {@code V1__baseline_schema.sql:409} 建好了 —— 结果 {@code /participant/getList} 返回合同、
 * {@code /participant/delete} 删合同，而需要的权限码是 {@code Participant_delete}。
 * 只被授予「参与方」权限的角色因此能删合同；反过来，配在 {@code asset.contract} 上的行级策略
 * 对这条路径完全无效（它解析成 {@code asset.participant}）。</p>
 *
 * <p>这个缺陷能长期存活，是因为它<b>不报错</b>：编译通过、启动通过、911 个后端测试全绿、
 * 前端也从不调用 {@code /participant/*}（没有 {@code baseUrl = 'participant'} 的服务类）。
 * 唯一能看见它的地方是「类名与泛型不一致」这一条纯结构事实 —— 所以把它做成机械守卫。</p>
 *
 * <h2>为什么零误伤</h2>
 *
 * <p>全仓 28 个 {@code *Controller extends Base*Controller<...>} 里，27 个本来就满足
 * {@code XController → XEntity / XService / XRepository}；单据类的后三个泛型是
 * {@code XDetailEntity / XDetailService / XDetailRepository}（{@code BaseBillController} 的 6 参约定）。
 * 也就是说这条规则上线时的豁免清单是<b>空的</b>：它不是为某个特例写的，而是把既有约定钉住。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("控制器身份 == 实体泛型（权限码/资源键/表 三者同源）")
class ControllerEntityBindingTest {

    /** 扫描根包。与 {@code PermissionService.loadPermission} 用的是同一个根 */
    private static final String ROOT_PACKAGE = "cn.hamm.spms";

    /**
     * 豁免清单：控制器简名 → 允许的实体简名。
     *
     * <p><b>刻意保持为空。</b>加条目等于承认「这个模块的权限码管的不是它自己的表」，
     * 必须在这里写下理由；如果理由成立，通常说明该拆模块而不是该豁免。</p>
     */
    private static final Map<String, String> ALLOWED_ENTITY_MISMATCH = Map.of();

    @Test
    @DisplayName("每个控制器的实体/服务/仓储泛型都必须与自己的类名同源")
    void controllerGenericsMustMatchItsOwnIdentity() {
        Map<String, List<String>> violations = new TreeMap<>();
        List<Class<?>> controllers = scanControllers();

        // 空扫描必须失败：扫描器坏了却"全部通过"是最坏的假阴性
        assertTrue(controllers.size() >= 25,
                "只扫到 " + controllers.size() + " 个控制器，扫描器可能失效（预期 >= 25）");

        // 双向自检：过滤条件既不能漏掉模块控制器，也不能把框架模板收进来
        List<String> names = controllers.stream().map(Class::getSimpleName).toList();
        assertTrue(names.contains("MaterialController") && names.contains("InputController"),
                "扫描漏掉了模块控制器（MaterialController / InputController 必须在内）：" + names);
        assertTrue(!names.contains("BaseController") && !names.contains("BaseBillController"),
                "扫描把框架模板收进来了：它们的泛型还是类型变量，判「违规」是假阳性：" + names);

        for (Class<?> controller : controllers) {
            String stem = stemOf(controller);
            Class<?>[] args = genericArgsOfBaseController(controller);
            if (args == null) {
                continue;
            }
            boolean isBill = BaseBillController.class.isAssignableFrom(controller);
            // 单据控制器是 6 参：前三是单据本身，后三是明细
            String[] expected = isBill
                    ? new String[]{stem + "Entity", stem + "Service", stem + "Repository",
                    stem + "DetailEntity", stem + "DetailService", stem + "DetailRepository"}
                    : new String[]{stem + "Entity", stem + "Service", stem + "Repository"};

            String allowedEntity = ALLOWED_ENTITY_MISMATCH.get(controller.getSimpleName());
            for (int i = 0; i < expected.length && i < args.length; i++) {
                // genericArgsOfBaseController 已经把 Type 收敛成 Class，非具体类（类型变量）是 null
                String actual = args[i] == null ? "<非具体类型>" : args[i].getSimpleName();
                if (i == 0 && allowedEntity != null && allowedEntity.equals(actual)) {
                    continue;
                }
                if (!expected[i].equals(actual)) {
                    violations.computeIfAbsent(controller.getSimpleName(), key -> new ArrayList<>())
                            .add("第 " + (i + 1) + " 个泛型应为 " + expected[i] + "，实际是 " + actual);
                }
            }
        }

        assertEquals(Map.of(), violations, () -> "控制器身份与泛型不同源（会导致权限码/资源键管到别的表上）："
                + describe(violations));
    }

    @Test
    @DisplayName("回归钉：participant 三件套必须指向 ParticipantEntity（曾指向 ContractEntity）")
    void participantModuleMustBindToParticipantEntity() {
        Class<?>[] args = genericArgsOfBaseController(classOf(
                "cn.hamm.spms.module.asset.contract.participant.ParticipantController"));
        assertTrue(args != null && args.length >= 3, "ParticipantController 不再继承 BaseController？请同步更新本测试");
        assertEquals("ParticipantEntity", args[0].getSimpleName(),
                "ParticipantController 的实体泛型又指回别的实体了：/participant/* 会读写那张表，"
                        + "而它要的权限码是 Participant_*");
        assertEquals("ParticipantService", args[1].getSimpleName());
        assertEquals("ParticipantRepository", args[2].getSimpleName());
    }

    @Test
    @DisplayName("规则自检：期望名推导对普通控制器与单据控制器都成立")
    void expectedNameDerivationIsCorrect() {
        assertEquals("MaterialEntity", stemOf(classOf(
                "cn.hamm.spms.module.asset.material.MaterialController")) + "Entity");
        assertEquals("PurchasePrice", stemOf(classOf(
                "cn.hamm.spms.module.channel.purchaseprice.PurchasePriceController")),
                "驼峰模块名不能被截断成 Purchase");
        assertEquals("InputDetailEntity", stemOf(classOf(
                "cn.hamm.spms.module.wms.input.InputController")) + "DetailEntity");
        // 反向自检：这条规则确实会咬住历史缺陷的那个形状。
        // 用真实的两个类推导，而不是比两个字面量（比字面量的断言永远为真，等于没自检）。
        String participantStem = stemOf(classOf(
                "cn.hamm.spms.module.asset.contract.participant.ParticipantController"));
        assertEquals("Participant", participantStem);
        assertNotEquals(participantStem + "Entity", ContractEntity.class.getSimpleName(),
                "自检失败：历史缺陷形状（Participant 控制器 + Contract 实体）必须被判为违规");
        assertEquals("ParticipantEntity", ParticipantEntity.class.getSimpleName(),
                "自检失败：修复后的期望值应与同包实体的真实简名一致");
    }

    // ------------------------------------------------------------------
    // 扫描与反射工具
    // ------------------------------------------------------------------

    /**
     * 扫出所有<em>已绑定</em>的 {@code BaseController} 子类（含 {@code BaseBillController} 的子类）。
     *
     * <p>判据是「这个类自己还声不声明类型参数」，而不是「是不是抽象类」：
     * {@code BaseBillController<E,S,R,DE,DS,DR>} 与 {@code BaseController<E,S,R>} 都是
     * <b>具体类</b>（{@code Modifier.isAbstract} 为假），Spring 的
     * {@code ClassPathScanningCandidateComponentProvider} 会把它们当候选收进来，
     * 而它们的泛型实参还是类型变量（{@code getActualTypeArguments()} 给出 {@code TypeVariable}），
     * 于是本守卫会把框架模板本身判成「第 1 个泛型应为 BaseBillEntity」——
     * 首轮实跑就是这么红的（{@code 实际是 <非具体类型>}）。
     * 模板类的特征恰好是 {@code getTypeParameters().length > 0}：它还在等人来绑定。
     * 真正落地的模块控制器（{@code MaterialController} 等）类型参数为空、泛型全是具体类。</p>
     */
    private List<Class<?>> scanControllers() {
        // false = 不用默认的 @Component 过滤，改用自己的 include filter
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseController.class));
        List<Class<?>> result = new ArrayList<>();
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(ROOT_PACKAGE);
        for (BeanDefinition candidate : candidates) {
            String className = candidate.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                // 框架模板（自己还带类型参数）与抽象中间层都不是「一个模块」，不参与绑定检查
                if (clazz.getTypeParameters().length > 0
                        || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                        || !BaseController.class.isAssignableFrom(clazz)) {
                    continue;
                }
                result.add(clazz);
            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                // 扫到无法加载的类（例如缺可选依赖）就跳过，不让守卫自己崩掉
            }
        }
        result.sort((left, right) -> left.getSimpleName().compareTo(right.getSimpleName()));
        return result;
    }

    /**
     * 取控制器在 {@code BaseController}/{@code BaseBillController} 那一层的实参。
     *
     * @return 泛型实参数组；控制器不是这两者的子类时返回 null
     */
    private Class<?>[] genericArgsOfBaseController(Class<?> controller) {
        Class<?> current = controller;
        while (current != null && current.getSuperclass() != null) {
            Class<?> parent = current.getSuperclass();
            if (parent == BaseController.class || parent == BaseBillController.class) {
                Type generic = current.getGenericSuperclass();
                if (!(generic instanceof ParameterizedType parameterized)) {
                    return null;
                }
                Type[] types = parameterized.getActualTypeArguments();
                Class<?>[] args = new Class<?>[types.length];
                for (int i = 0; i < types.length; i++) {
                    // 中间层若把泛型留成类型变量，这里会是 null，由调用方按字符串比对报出来
                    args[i] = types[i] instanceof Class<?> clazz ? clazz : null;
                }
                return args;
            }
            current = parent;
        }
        return null;
    }

    /** 控制器简名去掉 {@code Controller} 后缀 = 模块身份（{@code PurchasePriceController → PurchasePrice}） */
    private String stemOf(Class<?> controller) {
        String name = controller.getSimpleName();
        assertTrue(name.endsWith("Controller"), "控制器命名不符合约定：" + name);
        return name.substring(0, name.length() - "Controller".length());
    }

    private Class<?> classOf(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("类不存在：" + name, ex);
        }
    }

    private String describe(Map<String, List<String>> violations) {
        Map<String, String> flat = new LinkedHashMap<>();
        violations.forEach((key, value) -> flat.put(key, String.join("; ", value)));
        return flat.toString();
    }
}

package cn.hamm.spms.module.system.datapermission.rule;

import java.util.List;

/**
 * <h1>权限条件树（受控 AST）</h1>
 *
 * <p>{@code data_policy.condition_json} 解析后的<b>唯一</b>表示形式。
 * 三个消费方共用同一棵树，因此不可能出现"JPA 侧和 SQL 侧判定不一致"：</p>
 * <ul>
 *   <li>{@link JpaPermissionRuleCompiler} —— JPA Criteria（当前读链路）；</li>
 *   <li>{@link SqlPermissionRuleRenderer} —— 带命名参数的 SQL 片段（P2 MyBatis 拦截器）；</li>
 *   <li>{@link PermissionRuleEvaluator} —— 内存求值（影子比对与测试基准）。</li>
 * </ul>
 *
 * <p>设计约束：叶子只能引用 {@code field} + {@code value}/{@code contextVar}，
 * 值一律走参数绑定，<b>没有任何节点可以承载 SQL 文本</b>。</p>
 *
 * @author Hamm.cn
 */
public sealed interface PermissionRule {

    /**
     * 无条件（等价于改造前的行为）。
     *
     * <p>存量 {@code condition_json} 全是 {@code {}}，必须解析成这个而不是"拒绝"，
     * 否则内核一上线就把所有已授权查询打空。</p>
     */
    final class AllowAll implements PermissionRule {
        private static final AllowAll INSTANCE = new AllowAll();

        private AllowAll() {
        }

        public static AllowAll of() {
            return INSTANCE;
        }

        @Override
        public String toString() {
            return "ALLOW_ALL";
        }
    }

    /**
     * 恒假（用于"配置明确要拒绝"或"上下文变量缺失且策略要求严格"）
     */
    final class Deny implements PermissionRule {
        private static final Deny INSTANCE = new Deny();

        private Deny() {
        }

        public static Deny of() {
            return INSTANCE;
        }

        @Override
        public String toString() {
            return "DENY";
        }
    }

    /**
     * 分组节点
     *
     * @param op       {@code ALL_OF} / {@code ANY_OF}
     * @param children 子节点（1..N）
     */
    record Group(PermissionRuleOp op, List<PermissionRule> children) implements PermissionRule {
        public Group {
            children = List.copyOf(children);
        }
    }

    /**
     * 主体维度节点（<b>归属判定合一的载体</b>）
     *
     * <p>它取代的是原先散在 {@code DataPermissionSpecification} 里的一个 switch 加五个
     * {@code buildXxxPredicate}。那份逻辑回答的问题是"这条策略的主体维度该拿哪些候选列、
     * 用什么值去比、多个候选怎么组合"，而它<em>只活在 JPA 侧</em>；SQL 侧另有
     * {@code RowPermissionPredicate.tenant/owner/...} 一份同义实现（生产零调用，已删）。
     * 两套读语义要并存，判据必须先只有一份 —— 于是把"判据"做成节点，把"怎么绑到各自的列"
     * 留给各渲染器：</p>
     * <ul>
     *   <li>JPA：{@link JpaPathResolver#resolve} 逐个候选试绑，绑不上的跳过（跳过导致整棵无贡献时，
     *       由 {@code DataPermissionSpecification} 计入绑定失败，enforce 下恒假）；</li>
     *   <li>SQL：没有实体元模型可问，只能按登记的顺序取（见 {@link SqlPermissionRuleRenderer} 里
     *       {@code candidateFields} 的处理）；登记成关联路径（{@code storage.id}）时 JPA 侧能沿一跳绑上，
     *       SQL 侧要显式 JOIN，因此那里是<b>明确拒绝</b>而不是猜一个列名 —— 猜出来的列名一旦与别的列同名，
     *       表现是"多放行一些行"，查不出来。</li>
     * </ul>
     *
     * @param op              比较算子（归属判定只有 {@code EQ}）
     * @param candidateFields 候选列/属性名，来自 {@code data_resource} 登记；<b>顺序即优先级</b>
     * @param value           要比的值（主体 ID 或策略主体 ID）；为 {@code null} 表示"这一维没取到值"，
     *                        两个渲染器各自落成语义上的"不给看"（JPA：与 null 比；SQL：恒假）
     * @param orAllCandidates {@code true} = 所有能绑上的候选列 OR 起来（负责人档的多归属列语义）；
     *                        {@code false} = 只取第一个能绑上的（租户/部门/工厂/仓库档）
     */
    record Dimension(PermissionRuleOp op, List<String> candidateFields, Object value, boolean orAllCandidates)
            implements PermissionRule {

        public Dimension {
            candidateFields = List.copyOf(candidateFields);
        }

        /**
         * 资源根本没登记这一维（候选列表为空）—— 与"登记了但实体上绑不上"是两件事：
         * 前者不贡献条件，后者要被判据那边数出来
         */
        public boolean hasCandidates() {
            return !candidateFields.isEmpty();
        }
    }

    /**
     * 叶子节点
     *
     * @param op         比较算子
     * @param field      实体属性名或数据库列名（由消费方按各自映射解析）
     * @param value      常量值（{@code contextVar} 为空时必填）
     * @param contextVar 取当前主体的哪个维度（{@code userId}/{@code tenantId}/...，见 {@link PermissionRuleContext}）
     */
    record Leaf(PermissionRuleOp op, String field, Object value, String contextVar) implements PermissionRule {

        /**
         * 是否引用上下文变量
         */
        public boolean contextual() {
            return contextVar != null && !contextVar.isBlank();
        }
    }

    /**
     * 便捷构造：叶子
     */
    static Leaf leaf(PermissionRuleOp op, String field, Object value) {
        return new Leaf(op, field, value, null);
    }

    /**
     * 便捷构造：引用上下文变量的叶子
     */
    static Leaf leafFromContext(PermissionRuleOp op, String field, String contextVar) {
        return new Leaf(op, field, null, contextVar);
    }

    /**
     * 便捷构造：分组
     */
    static Group group(PermissionRuleOp op, List<PermissionRule> children) {
        return new Group(op, children);
    }

    /**
     * 树的深度（AllowAll/Deny 为 1）
     *
     * <p>用 instanceof 链而不是 switch 类型模式：项目仍是 Java 17，
     * 密封接口的 switch 模式匹配要到 21 才稳定可用。</p>
     */
    default int depth() {
        if (this instanceof Group group) {
            int childMax = 0;
            for (PermissionRule child : group.children()) {
                childMax = Math.max(childMax, child.depth());
            }
            return 1 + childMax;
        }
        return 1;
    }

    /**
     * 是否真正需要拼进查询条件（{@link AllowAll} 与空分组不参与）
     */
    default boolean contributes() {
        if (this instanceof Group group) {
            return !group.children().isEmpty();
        }
        return !(this instanceof AllowAll);
    }
}

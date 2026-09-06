package cn.hamm.spms.module.system.datapermission.rule;

/**
 * <h1>权限条件树算子</h1>
 *
 * <p>只允许这些受控算子，<b>不提供</b>任何"直接写 SQL 片段"的口子（无 {@code RAW}/{@code SQL}/{@code EXPRESSION}）。
 * JPA 与 SQL 两个渲染实现都只认这里定义的算子，避免两边能力集漂移。</p>
 *
 * @author Hamm.cn
 */
public enum PermissionRuleOp {

    /**
     * 子条件全部成立
     */
    ALL_OF(Kind.GROUP),
    /**
     * 子条件任一成立
     */
    ANY_OF(Kind.GROUP),

    EQ(Kind.LEAF),
    NE(Kind.LEAF),
    GT(Kind.LEAF),
    GE(Kind.LEAF),
    LT(Kind.LEAF),
    LE(Kind.LEAF),
    /**
     * 集合包含（空集合 → 恒假，不是恒真）
     */
    IN(Kind.LEAF),
    NOT_IN(Kind.LEAF),
    IS_NULL(Kind.LEAF),
    IS_NOT_NULL(Kind.LEAF),
    /**
     * 仅做前缀/包含匹配，值以参数绑定，不做字符串拼接
     */
    LIKE(Kind.LEAF);

    /**
     * 节点类别
     */
    public enum Kind {
        /** 分组节点（有 children） */
        GROUP,
        /** 叶子节点（有 field + value/contextVar） */
        LEAF
    }

    private final Kind kind;

    PermissionRuleOp(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isGroup() {
        return kind == Kind.GROUP;
    }

    /**
     * 该算子是否需要比较值
     */
    public boolean needsValue() {
        return this != IS_NULL && this != IS_NOT_NULL;
    }

    /**
     * 该算子的比较值必须是集合
     */
    public boolean needsCollection() {
        return this == IN || this == NOT_IN;
    }

    /**
     * 该算子在集合为空时必须判否（fail-closed 的关键：
     * {@code warehouse_id IN ()} 绝不能退化成"不过滤"）
     */
    public boolean deniesOnEmptyCollection() {
        return this == IN;
    }
}

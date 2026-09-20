package cn.hamm.spms.module.personnel.user.enums;

/**
 * <h1>用户令牌类型</h1>
 *
 * <p>这里只是应用自己的选项，不参与旧框架的字典校验或状态接口。
 * 键值/标签及 getter 保持原样；不再为枚举数据依赖框架接口或注解处理器。</p>
 *
 * @author Hamm.cn
 */
public enum UserTokenType {
    /**
     * 普通令牌
     */
    NORMAL(0, "普通令牌"),

    /**
     * 私人令牌
     */
    PERSONAL(1, "私人令牌"),

    /**
     * 第三方令牌
     */
    OAUTH2(2, "第三方令牌"),
    ;
    /**
     * 用户令牌类型
     */
    public static final String TYPE = "type";
    private final int key;
    private final String label;

    UserTokenType(int key, String label) {
        this.key = key;
        this.label = label;
    }

    /** 稳定的外部键值，不用 ordinal 代替。 */
    public int getKey() {
        return key;
    }

    /** 选项显示文案。 */
    public String getLabel() {
        return label;
    }
}

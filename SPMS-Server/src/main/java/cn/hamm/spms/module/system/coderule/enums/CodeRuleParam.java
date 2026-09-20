package cn.hamm.spms.module.system.coderule.enums;

/**
 * <h1>编码规则参数</h1>
 *
 * <p>这里只是应用自己的选项，不参与旧框架的字典校验或状态接口。
 * 键值/标签及 getter 保持原样；不再为枚举数据依赖框架接口或注解处理器。</p>
 *
 * @author Hamm.cn
 */
public enum CodeRuleParam {

    /**
     * 完整年份
     */
    FULL_YEAR(1, "yyyy", "完整年份", "2023"),

    /**
     * 年份
     */
    YEAR(2, "yy", "年份", "23"),

    /**
     * 月份
     */
    MONTH(3, "mm", "月份", "12"),

    /**
     * 日期
     */
    DATE(4, "dd", "日期", "31"),

    /**
     * 小时
     */
    HOUR(5, "hh", "小时", "20"),
    ;

    private final int key;
    private final String label;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 示例
     */
    private final String demo;

    CodeRuleParam(int key, String label, String desc, String demo) {
        this.key = key;
        this.label = label;
        this.desc = desc;
        this.demo = demo;
    }

    /** 稳定的外部键值，不用 ordinal 代替。 */
    public int getKey() {
        return key;
    }

    /** 选项显示文案。 */
    public String getLabel() {
        return label;
    }

    /** 编码参数说明。 */
    public String getDesc() {
        return desc;
    }

    /** 编码参数示例。 */
    public String getDemo() {
        return demo;
    }
}

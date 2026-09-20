package cn.hamm.spms.framework.util;

/**
 * <h1>客户、供应商与用户电话字段的兼容校验规则</h1>
 *
 * <p>由 Jakarta Pattern 消费，不自建验证注解或委托旧验证器。
 * 保留既有“手机号或座机”口径：空串可选，空白不可用；null 是否必填由独立非空约束决定。</p>
 *
 * @author Hamm.cn
 */
public final class PhoneRules {
    /** 可选电话：保持原区号、400/800、分机、国家码长度，不做 trim 或 Unicode 数字扩展。 */
    public static final String OPTIONAL_PHONE =
            "^(?:|(?:\\+\\d{1,4})?1[3-9]\\d{9}|(?:0\\d{2,3}-)?(?:\\d{7,8}|(?:400|800)\\d{7})(?:-\\d{1,4})?)$";

    /** 与已有客户/供应商字段校验文案相同。 */
    public static final String INVALID_PHONE_MESSAGE = "不是有效的电话号码";

    private PhoneRules() {
    }
}

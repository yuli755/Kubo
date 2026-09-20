package cn.hamm.spms.framework.util;

import java.math.BigDecimal;

/**
 * <h1>单据数量、价格的十进制运算</h1>
 *
 * <p>只保留业务正在使用的三个双操作数方法。用 valueOf 而不是 new BigDecimal(double)，
 * 避免把二进制浮点误差带进数量校验；不额外舍入，数据库精度仍由业务字段决定。</p>
 *
 * @author Hamm.cn
 */
public final class DecimalMath {
    private DecimalMath() {
    }

    /** 十进制求和，返回现有实体使用的 double。 */
    public static double add(double first, double second) {
        return BigDecimal.valueOf(first).add(BigDecimal.valueOf(second)).doubleValue();
    }

    /** 十进制求差，不做数量上下限判断。 */
    public static double subtract(double first, double second) {
        return BigDecimal.valueOf(first).subtract(BigDecimal.valueOf(second)).doubleValue();
    }

    /** 十进制求积，不截断价格或数量的小数位。 */
    public static double multiply(double first, double second) {
        return BigDecimal.valueOf(first).multiply(BigDecimal.valueOf(second)).doubleValue();
    }
}

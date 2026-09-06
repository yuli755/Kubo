package cn.hamm.spms.module.system.datapermission.rule;

/**
 * <h1>列名 / 属性名互转</h1>
 *
 * <p>{@code data_resource} 里登记的是数据库列名（{@code tenant_id}），
 * JPA 侧要的是实体属性名（{@code tenantId}），MyBatis/JSqlParser 侧要的是列名。
 * 条件树必须同时认这两种写法，否则就会出现"策略配了、条件生成不出来"的静默空转
 * —— 这正是历史实现最贵的一课。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleSupport {

    private PermissionRuleSupport() {
    }

    /**
     * snake_case → camelCase（已是 camelCase 时原样返回）
     */
    public static String toCamel(String value) {
        if (value == null || value.indexOf('_') < 0) {
            return value;
        }
        StringBuilder camel = new StringBuilder(value.length());
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            camel.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return camel.toString();
    }

    /**
     * camelCase → snake_case（已含下划线时原样返回）
     */
    public static String toSnake(String value) {
        if (value == null || value.indexOf('_') >= 0) {
            return value;
        }
        StringBuilder snake = new StringBuilder(value.length() + 4);
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (snake.length() > 0) {
                    snake.append('_');
                }
                snake.append(Character.toLowerCase(c));
            }
            else {
                snake.append(c);
            }
        }
        return snake.toString();
    }

    /**
     * 是否合法 SQL/JPA 标识符
     */
    public static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        if (!Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}

package cn.hamm.spms.framework.dictionary;

/**
 * <h1>枚举字典接口</h1>
 *
 * <p>规范系统内所有提供数字编码与展示文案的枚举项。
 * 替代旧 {@code airpower.core.interfaces.IDictionary}。</p>
 *
 * @author Hamm.cn
 */
public interface IDictionary {
    /**
     * 获取字典键值（数字码）
     */
    int getKey();

    /**
     * 获取字典标签文案
     */
    String getLabel();

    /**
     * 判断字典项的 key 是否等于给定数字码。
     *
     * <p>补齐 airpower 退场时遗漏的比较入口。{@code null} 一律视为不相等，
     * 与 {@code Objects.equals} 口径一致，不做拆箱以免 NPE。</p>
     *
     * @param key 待比较的数字码，可为 null
     * @return 相等返回 true
     */
    default boolean equalsKey(Integer key) {
        return key != null && getKey() == key.intValue();
    }

    /**
     * 判断字典项的 key 是否不等于给定数字码。
     *
     * @param key 待比较的数字码，可为 null
     * @return 不相等返回 true（key 为 null 时返回 true）
     */
    default boolean notEqualsKey(Integer key) {
        return !equalsKey(key);
    }
}

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
}

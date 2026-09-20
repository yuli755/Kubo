package cn.hamm.spms.common.dictionary;

import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.framework.util.EnumValues;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * <h1>枚举查找的应用错误边界</h1>
 *
 * <p>查找算法与字典接口无关；未知值仍返回既有 500 / 文案 / key-label 选项数据。
 * 本批不改变全局异常运行时，错误仍经本项目 Errors 发出。</p>
 *
 * @author Hamm.cn
 */
public final class EnumDictionaries {
    private EnumDictionaries() {
    }

    /** 查到即返回枚举；未知 key 不回退为默认枚举或 null。 */
    public static <E extends Enum<E>> E require(
            Class<E> type, int key, ToIntFunction<E> keyReader, Function<E, String> labelReader) {
        var match = EnumValues.find(type, key, keyReader);
        if (match.isPresent()) {
            return match.get();
        }
        Errors.SERVICE_ERROR.show("传入的值(" + type.getSimpleName() + "=" + key + ")不在字典可选范围内",
                EnumValues.options(type,
                        EnumValues.column("key", value -> keyReader.applyAsInt(value)),
                        EnumValues.column("label", labelReader)));
        // show 按契约必抛；即使未来错误边界被误改，也不能把未知枚举放行业务 switch。
        throw new IllegalStateException("字典错误边界未抛出异常");
    }
}

package cn.hamm.spms.base.coderule;

import cn.hamm.spms.core.interfaces.IDictionary;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <h1>序列号更新方式</h1>
 *
 * <p><b>2026-09-18 分层整改（L7）</b>：本枚举从 {@code module/system/coderule/enums} 搬到
 * {@code base/annotation} —— 它是纯字典值类型，而"用到它的最下层"是 {@link CodeRuleField}
 * （已在 base）。留在业务域就是让 base 反向依赖 module（门禁 L2 当场报红）。</p>
 *
 * @author Hamm.cn
 */
@AllArgsConstructor
@Getter
public enum SerialNumberUpdate implements IDictionary {

    /**
     * 按日更新
     */
    DAY(1, "按日更新", "yyyymmdd"),

    /**
     * 按月更新
     */
    MONTH(2, "按月更新", "yyyymm"),

    /**
     * 按年更新
     */
    YEAR(3, "按年更新", "yyyy"),

    /**
     * 不更新
     */
    NEVER(4, "不更新", ""),
    ;

    private final int key;
    private final String label;
    private final String defaultTemplate;
}

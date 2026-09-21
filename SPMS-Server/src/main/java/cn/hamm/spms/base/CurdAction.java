package cn.hamm.spms.base;

/**
 * <h1>实体通用校验动作组</h1>
 *
 * <p>定义添加、修改、必须传 ID 等场景下的通用 Bean Validation 校验分组。
 * 替代旧 {@code cn.hamm.airpower.curd.base.ICurdAction}，与现有实体校验规则完全同构兼容。</p>
 *
 * @author Hamm.cn
 */
public interface CurdAction {
    /**
     * ID 必须传入的场景
     */
    interface WhenIdRequired {
    }

    /**
     * 当添加时
     */
    interface WhenAdd {
    }

    /**
     * 当更新时
     */
    interface WhenUpdate {
    }
}

package cn.hamm.spms.base;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.core.annotation.ReadOnly;
import cn.hamm.spms.framework.curd.base.CurdEntity;
import lombok.Getter;

/**
 * <h1>应用实体基类</h1>
 *
 * @author Hamm.cn
 */
@Getter
@Description("")
public class BaseEntity<E extends BaseEntity<E>> extends CurdEntity<E> {
    @Description("是否已发布")
    @ReadOnly
    private Boolean isPublished = false;

    /**
     * 设置是否已发布
     *
     * @param isPublished 是否已发布
     * @return 实体
     */
    @SuppressWarnings("UnusedReturnValue")
    public E setIsPublished(Boolean isPublished) {
        this.isPublished = isPublished;
        //noinspection unchecked
        return (E) this;
    }
}

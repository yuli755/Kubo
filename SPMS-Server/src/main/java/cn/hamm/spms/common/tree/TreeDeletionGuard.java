package cn.hamm.spms.common.tree;

import cn.hamm.spms.common.exception.Errors;
import cn.hamm.spms.framework.util.Trees;

import java.util.List;
import java.util.function.Function;

/**
 * <h1>树节点删除的应用错误边界</h1>
 *
 * @author Hamm.cn
 */
public final class TreeDeletionGuard {
    private TreeDeletionGuard() {
    }

    /** 保持原有错误 code=500 和提示文案，不在工具退场时改变 API 错误协议。 */
    public static void ensureNoChildrenBeforeDelete(long id, Function<Long, ? extends List<?>> finder) {
        Errors.SERVICE_ERROR.when(Trees.hasChildren(id, finder), "无法删除含有下级的数据，请先删除所有下级！");
    }
}

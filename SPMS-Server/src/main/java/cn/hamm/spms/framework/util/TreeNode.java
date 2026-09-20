package cn.hamm.spms.framework.util;

import java.util.List;

/**
 * <h1>树组装的最小数据契约</h1>
 *
 * <p>不继承 ORM 实体或框架 IEntity：树处理只需要 ID、父 ID 和 children 写入。</p>
 *
 * @author Hamm.cn
 */
public interface TreeNode<E extends TreeNode<E>> {
    /** 已持久化的节点 ID；不能为 null 或根占位值 0。 */
    Long getId();

    /** null 与 0 均表示根；未出现在当前列表中的父节点不会被补造。 */
    Long getParentId();

    /** 接收组装后的只读子节点列表并返回本节点。 */
    E setChildren(List<E> children);
}

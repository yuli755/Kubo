package cn.hamm.spms.framework.curd.datapermission;

/**
 * <h1>数据权限动作</h1>

 * <p><b>2026-09-18 分层搬迁</b>：本类型原在 {@code cn.hamm.spms.module.system.datapermission.enums}，
 * 由<b>框架层</b>搬来 —— 它是纯共享内核（只依赖 JDK），却被 {@code base/BaseService}、
 * {@code base/TenantAwareEntity} 这些下层类使用，构成"下层依赖业务域"的反向依赖。
 * 权限的<b>实现</b>（策略存储、拦截器、审计落库、规则编译）仍留在
 * {@code module.system.datapermission}；这里只留<b>抽象与上下文</b>。</p>
 *
 * @author Hamm.cn
 */
public enum DataPermissionAction {
    QUERY,
    DETAIL,
    CREATE,
    UPDATE,
    DELETE,
    EXPORT
}

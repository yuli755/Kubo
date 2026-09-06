package cn.hamm.spms.module.system.datapermission.rule;

/**
 * <h1>条件树非法异常</h1>
 *
 * <p>解析/校验不过就抛这个异常，调用方（{@code DataPermissionSpecification}）
 * 在 enforce 下必须按"拒绝"处理，<b>不允许</b> catch 后当"无权限条件"放行 ——
 * 那正是历史空转的形状。</p>
 *
 * @author Hamm.cn
 */
public class PermissionRuleException extends RuntimeException {

    private final String resourceKey;

    public PermissionRuleException(String resourceKey, String message) {
        super(message);
        this.resourceKey = resourceKey;
    }

    /**
     * @return 出错的数据资源标识（用于 binding 指标打点）
     */
    public String resourceKey() {
        return resourceKey;
    }
}

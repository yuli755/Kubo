package cn.hamm.spms.module.system.dashboard;

import cn.hamm.spms.base.ApiController;
import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.http.ApiResponse;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * <h1>数据看板控制器</h1>
 *
 * <p>业务概况是「当前用户可见范围内的统计」，不是跨租户管理数据，因此只要求登录
 * （{@code @Permission(authorize = false)}），不做粗粒度动作鉴权 —— 行级收敛由各资源的
 * {@code countByPermission} 完成，返回内容本身已被过滤。</p>
 *
 * @author Hamm.cn
 */
@Api("dashboard")
@Description("数据看板")
public class DashboardController extends ApiController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("overview")
    @Description("业务概况")
    @Permission(authorize = false)
    public ApiResponse<DashboardService.DashboardOverview> overview() {
        return ApiResponse.data(dashboardService.overview());
    }
}

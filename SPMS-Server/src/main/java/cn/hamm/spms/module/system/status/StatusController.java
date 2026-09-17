package cn.hamm.spms.module.system.status;

import cn.hamm.spms.framework.api.ApiController;
import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.permission.Permission;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Permission(login = false)
@Api("status")
@Description("系统状态")
public class StatusController extends ApiController {
}

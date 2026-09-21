package cn.hamm.spms.module.system.status;

import cn.hamm.spms.base.ApiController;
import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Permission;

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

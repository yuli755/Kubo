package cn.hamm.spms.module.system.config;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseController;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("config")
@Description("系统配置")
public class ConfigController extends BaseController<ConfigEntity, ConfigService, ConfigRepository> {
}

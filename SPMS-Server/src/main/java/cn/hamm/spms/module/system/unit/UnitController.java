package cn.hamm.spms.module.system.unit;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseController;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("unit")
@Description("单位")
public class UnitController extends BaseController<UnitEntity, UnitService, UnitRepository> {
}

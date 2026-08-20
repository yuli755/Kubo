package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;

/**
 * <h1>数据资源管理控制器</h1>
 *
 * @author Hamm.cn
 */
@Api("dataResource")
@Description("数据资源")
public class DataResourceController extends BaseController<DataResourceEntity, DataResourceService, DataResourceRepository> {
}
package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataPolicyRepository;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;

/**
 * <h1>数据权限策略管理控制器</h1>
 *
 * @author Hamm.cn
 */
@Api("dataPolicy")
@Description("数据权限策略")
public class DataPolicyController extends BaseController<DataPolicyEntity, DataPolicyService, DataPolicyRepository> {
}
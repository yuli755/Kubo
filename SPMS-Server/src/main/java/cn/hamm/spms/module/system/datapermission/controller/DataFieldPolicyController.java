package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;

/**
 * <h1>数据字段权限策略管理控制器</h1>
 *
 * @author Hamm.cn
 */
@Api("dataFieldPolicy")
@Description("数据字段权限策略")
public class DataFieldPolicyController extends BaseController<DataFieldPolicyEntity, DataFieldPolicyService, DataFieldPolicyRepository> {
}
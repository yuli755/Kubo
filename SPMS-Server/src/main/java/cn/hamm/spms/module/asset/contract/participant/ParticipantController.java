package cn.hamm.spms.module.asset.contract.participant;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.asset.contract.ContractEntity;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("participant")
@Description("参与方")
public class ParticipantController extends BaseController<ContractEntity, ParticipantService, ParticipantRepository> {
}

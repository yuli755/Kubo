package cn.hamm.spms.module.asset.contract.participant;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.BaseController;

/**
 * <h1>Controller</h1>
 *
 * <p>参与方是合同的 {@code @ManyToMany} 子表（{@code ContractEntity.participantList}，
 * {@code cascade = PERSIST}）：只能随合同创建，改不动也删不掉。所以这里<b>刻意排除 Delete</b>——
 * 一个已被合同引用的参与方，从这条路径删掉会留下悬挂的中间表行；要摘掉某个参与方，
 * 正确做法是在合同侧更新 {@code participantList}。</p>
 *
 * <p>保留 update / getDetail / getList 是为了「合同上录错了联系电话或证件号」这类订正场景：
 * cascade 只有 PERSIST，合同侧的更新不会落到既有参与方行上，没有这条路径就永远改不了。</p>
 *
 * @author Hamm.cn
 */
@Api("participant")
@Description("参与方")
@Extends(exclude = {Curd.Delete})
public class ParticipantController extends BaseController<ParticipantEntity, ParticipantService, ParticipantRepository> {
}

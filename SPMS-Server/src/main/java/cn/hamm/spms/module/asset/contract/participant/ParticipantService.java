package cn.hamm.spms.module.asset.contract.participant;

import cn.hamm.spms.base.BaseService;
import org.springframework.stereotype.Service;

/**
 * <h1>Service</h1>
 *
 * <p>泛型必须是 {@link ParticipantEntity}：权限身份（{@code Participant_*}）与数据权限资源键
 * （{@code asset.participant}）都由这个类推导，一旦指到别的实体上，就会出现「拿着参与方权限
 * 却能读写那张表」的越权，且行级策略作用在错误的表上。见 {@code ControllerEntityBindingTest}。</p>
 *
 * @author Hamm.cn
 */
@Service
public class ParticipantService extends BaseService<ParticipantEntity, ParticipantRepository> {
}

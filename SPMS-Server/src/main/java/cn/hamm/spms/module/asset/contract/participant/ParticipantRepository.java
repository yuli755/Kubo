package cn.hamm.spms.module.asset.contract.participant;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

/**
 * <h1>合同参与方数据源</h1>
 *
 * @apiNote J3：这个接口原来绑的是 ContractEntity（复制粘贴留下的），于是 {@code participant} 这条 API
 * 读写的其实是合同表。多对多回填要按目标实体找仓储，这个错绑会直接让"参与方列表"取到合同行，所以一并修正。
 *
 * @author Hamm.cn
 */
@Repository
public interface ParticipantRepository extends BaseRepository<ParticipantEntity> {
}

package cn.hamm.spms.module.asset.contract;

import cn.hamm.spms.module.asset.contract.enums.ContractStatus;
import cn.hamm.spms.module.asset.contract.enums.ParticipantRole;
import cn.hamm.spms.module.asset.contract.participant.ParticipantEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>合同「生效」领域闸门回归（N-15）</h1>
 *
 * <p>实测事故：0 参与方的合同直接调 {@code POST /contract/enforce} 被生效
 * （status 1→2，contract_participant_list 为空），因为后端 {@code enforce()}
 * 只查状态、不查参与方。本类钉死 {@link ContractService#canEnforce} 纯函数：
 * 生效前必须有 ≥2 个不同角色，且判据落后端 —— 不因前端 toast 提前说明就放过后端闸门。</p>
 *
 * @author Hamm.cn
 */
class ContractEnforceGateTest {

    private final ContractService service = new ContractService();

    private ParticipantEntity participant(Integer role) {
        return new ParticipantEntity().setRole(role);
    }

    private ContractEntity contract(ContractStatus status, Set<ParticipantEntity> participants) {
        ContractEntity contract = new ContractEntity();
        contract.setStatus(status.getKey());
        contract.setParticipantList(participants);
        return contract;
    }

    @Test
    @DisplayName("0 参与方 → 不可生效（N-15 核心负例）")
    void zeroParticipantRejected() {
        assertFalse(service.canEnforce(contract(ContractStatus.INVALID, new LinkedHashSet<>())),
                "0 参与方合同不得生效");
    }

    @Test
    @DisplayName("null 参与方列表 → 按 0 处理，不可生效")
    void nullParticipantListRejected() {
        assertFalse(service.canEnforce(contract(ContractStatus.INVALID, null)),
                "null 参与方列表不得生效");
    }

    @Test
    @DisplayName("1 个参与方 → 不可生效")
    void singleParticipantRejected() {
        Set<ParticipantEntity> one = Set.of(participant(ParticipantRole.A.getKey()));
        assertFalse(service.canEnforce(contract(ContractStatus.INVALID, one)),
                "仅一方不得生效");
    }

    @Test
    @DisplayName("同角色 2 方（去重后 1）→ 不可生效")
    void sameRoleTwoParticipantsRejected() {
        Set<ParticipantEntity> same = new LinkedHashSet<>();
        same.add(participant(ParticipantRole.A.getKey()));
        same.add(participant(ParticipantRole.A.getKey()));
        assertFalse(service.canEnforce(contract(ContractStatus.INVALID, same)),
                "角色去重后不足两方不得生效");
    }

    @Test
    @DisplayName("2 个不同角色 → 可生效")
    void twoDistinctRolesPass() {
        Set<ParticipantEntity> two = new LinkedHashSet<>();
        two.add(participant(ParticipantRole.A.getKey()));
        two.add(participant(ParticipantRole.B.getKey()));
        assertTrue(service.canEnforce(contract(ContractStatus.INVALID, two)));
    }

    @Test
    @DisplayName("状态闸门独立成立：已生效合同即使参与方足够也不可再次生效")
    void statusGateStillHolds() {
        Set<ParticipantEntity> two = new LinkedHashSet<>();
        two.add(participant(ParticipantRole.A.getKey()));
        two.add(participant(ParticipantRole.B.getKey()));
        assertFalse(service.canEnforce(contract(ContractStatus.EFFECTIVE, two)),
                "已生效合同不得再次生效");
    }
}

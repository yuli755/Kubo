package cn.hamm.spms.base.bill;

import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.channel.purchase.PurchaseService;
import cn.hamm.spms.module.channel.purchase.enums.PurchaseStatus;
import cn.hamm.spms.module.wms.input.InputService;
import cn.hamm.spms.module.wms.input.enums.InputStatus;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.move.MoveService;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.move.enums.MoveStatus;
import cn.hamm.spms.module.wms.output.OutputService;
import cn.hamm.spms.module.wms.output.OutputEntity;
import cn.hamm.spms.module.wms.output.enums.OutputStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>单据状态机闸门测试（阶段 C-1 回归）</h1>
 *
 * <p>addFinish 的状态闸门只认「已审核/执行中」。两道被实测的事故：
 * AUDITING 单未审核先动库存；Move 完成自动生成的 DONE 入/出库单因明细
 * isFinished=false 可被再次录入，库存二次扣减。闸门判定本身是纯函数，
 * 在这里钉死各单据的状态矩阵。</p>
 *
 * @author Hamm.cn
 */
class BillStateMachineGateTest {

    private InputEntity input(InputStatus status) {
        InputEntity bill = new InputEntity();
        bill.setStatus(status.getKey());
        return bill;
    }

    private OutputEntity output(OutputStatus status) {
        OutputEntity bill = new OutputEntity();
        bill.setStatus(status.getKey());
        return bill;
    }

    private MoveEntity move(MoveStatus status) {
        MoveEntity bill = new MoveEntity();
        bill.setStatus(status.getKey());
        return bill;
    }

    private PurchaseEntity purchase(PurchaseStatus status) {
        PurchaseEntity bill = new PurchaseEntity();
        bill.setStatus(status.getKey());
        return bill;
    }

    @Test
    @DisplayName("采购单：仅 PURCHASING 可录入完成量，DONE 不得重复录入")
    void purchaseFinishGate() {
        PurchaseService service = new PurchaseService();
        assertFalse(service.canAddFinish(purchase(PurchaseStatus.AUDITING)));
        assertFalse(service.canAddFinish(purchase(PurchaseStatus.REJECTED)));
        assertTrue(service.canAddFinish(purchase(PurchaseStatus.PURCHASING)));
        assertFalse(service.canAddFinish(purchase(PurchaseStatus.INPUTTING)));
        assertFalse(service.canAddFinish(purchase(PurchaseStatus.DONE)));
    }

    @Test
    @DisplayName("入库单：仅 INPUTTING 可录入完成量")
    void inputFinishGate() {
        InputService service = new InputService();
        assertFalse(service.canAddFinish(input(InputStatus.AUDITING)), "未审核不得录入：否则审核变追认");
        assertFalse(service.canAddFinish(input(InputStatus.REJECTED)));
        assertTrue(service.canAddFinish(input(InputStatus.INPUTTING)));
        assertFalse(service.canAddFinish(input(InputStatus.DONE)), "已完成单不得重复执行");
    }

    @Test
    @DisplayName("出库单：仅 OUTPUTTING 可录入完成量（含 Move 生成的 DONE 单被拦）")
    void outputFinishGate() {
        OutputService service = new OutputService();
        assertFalse(service.canAddFinish(output(OutputStatus.AUDITING)));
        assertFalse(service.canAddFinish(output(OutputStatus.REJECTED)));
        assertTrue(service.canAddFinish(output(OutputStatus.OUTPUTTING)));
        // C-1 核心现场：Move 完成后自动生成的出库单就是 DONE 态，必须拦住
        assertFalse(service.canAddFinish(output(OutputStatus.DONE)),
                "自动生成的 DONE 出库单再录入会把已搬完的库存二次扣减");
    }

    @Test
    @DisplayName("移库单：仅 MOVING 可录入完成量")
    void moveFinishGate() {
        MoveService service = new MoveService();
        assertFalse(service.canAddFinish(move(MoveStatus.AUDITING)));
        assertFalse(service.canAddFinish(move(MoveStatus.REJECTED)));
        assertTrue(service.canAddFinish(move(MoveStatus.MOVING)));
        assertFalse(service.canAddFinish(move(MoveStatus.DONE)));
    }

    @Test
    @DisplayName("既有审核/驳回/编辑闸门语义不变")
    void existingGatesUnchanged() {
        InputService service = new InputService();
        assertTrue(service.canAudit(input(InputStatus.AUDITING)));
        assertFalse(service.canAudit(input(InputStatus.INPUTTING)), "重复审核仍被状态拦");
        assertTrue(service.canReject(input(InputStatus.AUDITING)));
        assertFalse(service.canReject(input(InputStatus.REJECTED)));
        assertTrue(service.canEdit(input(InputStatus.REJECTED)), "仅驳回后可编辑（回到审核中）");
        assertFalse(service.canEdit(input(InputStatus.INPUTTING)));
    }
}

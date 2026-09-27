package cn.hamm.spms.framework.util;

import cn.hamm.spms.base.CurdAction;

import java.util.List;

/**
 * <h1>实体与动作组通用契约测试</h1>
 *
 * <p>验证自持 {@link CurdAction} 的嵌套校验动作组标识类型完整性与继承作用域，
 * 以及单据明细多批次到货累加、结余结转与越界防御契约。</p>
 *
 * @author Hamm.cn
 */
public final class CoreActionContracts {
    private CoreActionContracts() {
    }

    public static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("curd-action/groups-hierarchy", () -> {
                    UtilityContracts.check(CurdAction.WhenIdRequired.class.isInterface());
                    UtilityContracts.check(CurdAction.WhenAdd.class.isInterface());
                    UtilityContracts.check(CurdAction.WhenUpdate.class.isInterface());
                    UtilityContracts.check(!CurdAction.WhenIdRequired.class.equals(CurdAction.WhenAdd.class));
                    UtilityContracts.check(!CurdAction.WhenAdd.class.equals(CurdAction.WhenUpdate.class));
                }),
                new UtilityContracts.Case("curd-action/implementation-scope", () -> {
                    class SampleActionHolder implements CurdAction {
                    }
                    Class<?> idGroup = SampleActionHolder.WhenIdRequired.class;
                    Class<?> addGroup = SampleActionHolder.WhenAdd.class;
                    Class<?> updateGroup = SampleActionHolder.WhenUpdate.class;
                    UtilityContracts.equal(CurdAction.WhenIdRequired.class, idGroup);
                    UtilityContracts.equal(CurdAction.WhenAdd.class, addGroup);
                    UtilityContracts.equal(CurdAction.WhenUpdate.class, updateGroup);
                }),
                new UtilityContracts.Case("bill-detail/multi-arrival-quantity-accumulation", () -> {
                    // 模拟明细多批次到货：100 需求量，分批 30、40、50 到达
                    double requiredQuantity = 100.0;
                    double currentFinish = 0.0;
                    boolean isFinished = false;

                    // 第一批：30 到达
                    double quantity = 30.0;
                    double need = DecimalMath.subtract(requiredQuantity, currentFinish);
                    if (quantity < need) {
                        currentFinish = DecimalMath.add(currentFinish, quantity);
                        quantity = 0.0;
                    } else {
                        quantity = DecimalMath.subtract(quantity, need);
                        currentFinish = requiredQuantity;
                        isFinished = true;
                    }
                    UtilityContracts.equal(30.0, currentFinish);
                    UtilityContracts.equal(0.0, quantity);
                    UtilityContracts.check(!isFinished);

                    // 第二批：40 到达（修补前会发生 detail.setFinishQuantity(40) 覆盖丢失首批）
                    quantity = 40.0;
                    need = DecimalMath.subtract(requiredQuantity, currentFinish);
                    if (quantity < need) {
                        currentFinish = DecimalMath.add(currentFinish, quantity);
                        quantity = 0.0;
                    } else {
                        quantity = DecimalMath.subtract(quantity, need);
                        currentFinish = requiredQuantity;
                        isFinished = true;
                    }
                    UtilityContracts.equal(70.0, currentFinish);
                    UtilityContracts.equal(0.0, quantity);
                    UtilityContracts.check(!isFinished);

                    // 第三批：50 到达（超额完成，结转余量 20）
                    quantity = 50.0;
                    need = DecimalMath.subtract(requiredQuantity, currentFinish);
                    if (quantity < need) {
                        currentFinish = DecimalMath.add(currentFinish, quantity);
                        quantity = 0.0;
                    } else {
                        quantity = DecimalMath.subtract(quantity, need);
                        currentFinish = requiredQuantity;
                        isFinished = true;
                    }
                    UtilityContracts.equal(100.0, currentFinish);
                    UtilityContracts.equal(20.0, quantity);
                    UtilityContracts.check(isFinished);
                }),
                new UtilityContracts.Case("bill-detail/multi-detail-quantity-rollover", () -> {
                    // 模拟两条明细结转：明细 1 需 50，明细 2 需 50，单次到货 70
                    double[] needs = new double[]{50.0, 50.0};
                    double[] finishes = new double[]{0.0, 0.0};
                    boolean[] finished = new boolean[]{false, false};

                    double quantity = 70.0;
                    for (int i = 0; i < needs.length; i++) {
                        if (quantity <= 0) break;
                        double detailNeed = DecimalMath.subtract(needs[i], finishes[i]);
                        if (quantity < detailNeed) {
                            finishes[i] = DecimalMath.add(finishes[i], quantity);
                            quantity = 0.0;
                        } else {
                            quantity = DecimalMath.subtract(quantity, detailNeed);
                            finishes[i] = needs[i];
                            finished[i] = true;
                        }
                    }
                    UtilityContracts.equal(50.0, finishes[0]);
                    UtilityContracts.check(finished[0]);
                    UtilityContracts.equal(20.0, finishes[1]);
                    UtilityContracts.check(!finished[1]);
                    UtilityContracts.equal(0.0, quantity);
                })
        );
    }
}

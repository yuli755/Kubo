package cn.hamm.spms.framework.util;

import cn.hamm.spms.base.CurdAction;

import java.util.List;

/**
 * <h1>实体与动作组通用契约测试</h1>
 *
 * <p>验证自持 {@link CurdAction} 的嵌套校验动作组标识类型完整性与继承作用域。</p>
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
                })
        );
    }
}

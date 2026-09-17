package cn.hamm.spms.base;

import static cn.hamm.spms.module.system.coderule.enums.SerialNumberUpdate.*;

/** 反例 L2（通配符静态导入）：旧判据解析不到这一行，等于免检 —— 本用例就是它的回归测试 */
public class BadWildcard {
    private SerialNumberUpdate type = SerialNumberUpdate.DAY;
}

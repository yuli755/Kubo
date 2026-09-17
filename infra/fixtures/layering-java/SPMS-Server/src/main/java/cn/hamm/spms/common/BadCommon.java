package cn.hamm.spms.common;

import cn.hamm.spms.module.channel.sale.SaleLeak;

/** 反例 L7：common 不该依赖 module */
public class BadCommon {
    private SaleLeak saleLeak;
}

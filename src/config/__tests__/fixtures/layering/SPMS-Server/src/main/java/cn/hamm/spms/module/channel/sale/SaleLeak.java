package cn.hamm.spms.module.channel.sale;

import cn.hamm.spms.module.wms.output.repository.LeakRepository;

/** 反例 L6：跨域不该伸手进对方的 repository */
public class SaleLeak {
    private LeakRepository leakRepository;
}

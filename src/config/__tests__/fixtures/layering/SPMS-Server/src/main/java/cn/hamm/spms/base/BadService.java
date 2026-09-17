package cn.hamm.spms.base;

import cn.hamm.spms.module.wms.output.repository.OutputRepository;

/** 反例 L2：base 不该依赖 module */
public class BadService {
    private OutputRepository outputRepository;
}

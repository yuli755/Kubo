package cn.hamm.spms.module.wms.output.controller;

import cn.hamm.spms.module.wms.output.repository.LeakRepository;

/** 反例 L5：控制器不该注入仓储 */
public class BadController {
    private LeakRepository leakRepository;
}

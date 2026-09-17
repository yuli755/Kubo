package cn.hamm.spms.module.wms.output.repository;

import cn.hamm.spms.module.wms.output.service.OutputService;

/** 反例 L3：repository 不该依赖 service */
public class BadRepository {
    private OutputService outputService;
}

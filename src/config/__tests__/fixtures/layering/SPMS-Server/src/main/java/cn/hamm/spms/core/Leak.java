package cn.hamm.spms.core;

import cn.hamm.spms.framework.curd.base.CurdService;

/** 反例 L0：core 不该依赖 framework */
public class Leak {
    private CurdService<?, ?> service;
}

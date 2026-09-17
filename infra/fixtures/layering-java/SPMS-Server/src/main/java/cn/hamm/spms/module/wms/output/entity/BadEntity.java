package cn.hamm.spms.module.wms.output.entity;

import cn.hamm.spms.module.wms.output.repository.BadRepository;

/** 反例 L4：实体不该依赖 repository */
public class BadEntity {
    private BadRepository repository;
}

package cn.hamm.spms.module.wms.input.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;
import cn.hamm.spms.module.wms.input.InputEntity;

import java.util.Map;

/**
 * 入库单读侧 Mapper；只承载 XML 读语句与 ORM parity 探针，写路径仍走 JPA。
 *
 * @author Hamm.cn
 */
@DataResource(key = "wms.input", alias = "input")
public interface InputMapper extends KuboBaseMapper<InputEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "wms.input";
    }

    /**
     * 按统一权限片段统计入库单行数。
     *
     * @param parameters fragment 与命名参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}

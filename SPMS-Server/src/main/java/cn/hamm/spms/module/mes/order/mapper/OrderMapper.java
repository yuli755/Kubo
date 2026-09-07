package cn.hamm.spms.module.mes.order.mapper;

import cn.hamm.spms.base.mapper.KuboBaseMapper;
import cn.hamm.spms.module.mes.order.OrderEntity;
import cn.hamm.spms.module.system.datapermission.annotation.DataResource;
import cn.hamm.spms.module.system.datapermission.service.RowParityCounter;

import java.util.Map;

/**
 * 生产订单读侧 Mapper；只承载 XML 读语句与 ORM parity 探针，写路径仍走 JPA。
 *
 * @author Hamm.cn
 */
@DataResource(key = "mes.order", alias = "orders")
public interface OrderMapper extends KuboBaseMapper<OrderEntity>, RowParityCounter {

    @Override
    default String parityResourceKey() {
        return "mes.order";
    }

    /**
     * 按统一权限片段统计生产订单行数。
     *
     * @param parameters fragment 与命名参数
     * @return 行数
     */
    Long countByPermissionFragment(Map<String, Object> parameters);
}

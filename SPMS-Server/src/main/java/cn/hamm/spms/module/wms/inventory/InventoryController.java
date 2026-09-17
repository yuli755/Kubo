package cn.hamm.spms.module.wms.inventory;

import cn.hamm.spms.framework.api.annotation.Api;
import cn.hamm.spms.core.Json;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.framework.curd.annotation.Extends;
import cn.hamm.spms.framework.curd.base.Curd;
import cn.hamm.spms.framework.curd.model.query.QueryListRequest;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.base.BaseController;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.spms.framework.curd.base.Curd.GetDetail;
import static cn.hamm.spms.framework.curd.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * <p>阶段 C-9：库存行只读。BaseController 默认放行 Add/Update/Delete，
 * 实测 POST /inventory/update 可以把任意库存行直接改写成 999999——绕开全部
 * 单据流与状态机，账实差异无从追溯。库存的增减只允许经入/出/转单据驱动
 * （服务内部走 InventoryService，不经 HTTP），所以这里显式排除三个写口。
 * 前端只用 getPage/getDetail/getCount/getStat/export，不受影响。</p>
 *
 * @author Hamm.cn
 */
@Api("inventory")
@Description("库存")
@Extends(value = {GetDetail, GetPage, Curd.Export, Curd.QueryExport}, exclude = {Curd.Add, Curd.Update, Curd.Delete})
public class InventoryController extends BaseController<InventoryEntity, InventoryService, InventoryRepository> {

    /**
     * 库存分页读出口（Mapper）；开关关闭时回到 AirPower JPA。
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<InventoryEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 库存详情读出口（Mapper）；无权行不回退 JPA。
     */
    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull InventoryEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        InventoryEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }

    /**
     * 库存总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询库存总数")
    @PostMapping("getCount")
    public Json getCount(@RequestBody(required = false) QueryListRequest<InventoryEntity> request) {
        InventoryEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperCount(filter));
    }

    /**
     * 库存统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询库存统计")
    @PostMapping("getStat")
    public Json getStat(@RequestBody(required = false) QueryListRequest<InventoryEntity> request) {
        InventoryEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperStat(filter));
    }

    /**
     * 库存导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     * 返回 fileCode 契约与框架一致，前端 queryExport 不变。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<InventoryEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return Json.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}

package cn.hamm.spms.module.channel.purchaseprice;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.spms.base.Curd.GetDetail;
import static cn.hamm.spms.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；读接口在本类显式换到 PurchasePriceMapper，
 * 让 page/detail/stat/count/export 都经过同一条 statement 绑定和行级权限拦截链。</p>
 *
 * @author Hamm.cn
 */
@Api("purchasePrice")
@Description("采购价格")
@Extends({Curd.Export, Curd.QueryExport})
public class PurchasePriceController extends BaseController<
        PurchasePriceEntity, PurchasePriceService, PurchasePriceRepository
        > implements IPurchasePriceAction {

    /**
     * 采购价分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<PurchasePriceEntity>> getPage(@RequestBody QueryPageRequest<PurchasePriceEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    /**
     * 采购价详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public ApiResponse<PurchasePriceEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull PurchasePriceEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        PurchasePriceEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 采购价总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询采购价总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<PurchasePriceEntity> request) {
        PurchasePriceEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 采购价统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询采购价统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<PurchasePriceEntity> request) {
        PurchasePriceEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 采购价导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<PurchasePriceEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }

    @Description("查询物料和供应商的价格")
    @PostMapping("getByMaterialAndSupplier")
    public ApiResponse<PurchasePriceEntity> getByMaterialAndSupplier(@RequestBody @Validated(WhenGetByMaterialAndSupplier.class) PurchasePriceEntity purchasePrice) {
        return ApiResponse.data(
                service.getByMaterialAndSupplier(
                        purchasePrice.getMaterial(),
                        purchasePrice.getSupplier()
                )
        );
    }
}

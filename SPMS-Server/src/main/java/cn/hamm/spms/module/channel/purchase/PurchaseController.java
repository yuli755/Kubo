package cn.hamm.spms.module.channel.purchase;

import cn.hamm.spms.framework.web.annotation.Api;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.base.annotation.Extends;
import cn.hamm.spms.base.Curd;
import cn.hamm.spms.base.bill.BaseBillController;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.framework.query.QueryPageResponse;
import cn.hamm.spms.framework.http.ApiResponse;
import org.jetbrains.annotations.NotNull;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailEntity;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailRepository;
import cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.spms.base.Curd.GetDetail;
import static cn.hamm.spms.base.Curd.GetPage;
import static cn.hamm.spms.common.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * @author Hamm.cn
 */
@Api("purchase")
@Description("采购单")
@Extends(value = {Curd.Export, Curd.QueryExport}, exclude = Curd.Delete)
public class PurchaseController extends BaseBillController<
        PurchaseEntity, PurchaseService, PurchaseRepository,
        PurchaseDetailEntity, PurchaseDetailService, PurchaseDetailRepository
        > {

    /**
     * 采购单分页读出口（Mapper）；开关关闭时回退 AirPower JPA。
     */
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<PurchaseEntity>> getPage(@RequestBody QueryPageRequest<PurchaseEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    /**
     * 采购单详情读出口（Mapper）；无权行不回退 JPA。
     */
    @Override
    @PostMapping("getDetail")
    public ApiResponse<PurchaseEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull PurchaseEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        PurchaseEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 采购单总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询采购单总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<PurchaseEntity> request) {
        PurchaseEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 采购单统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询采购单统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<PurchaseEntity> request) {
        PurchaseEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 采购单导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<PurchaseEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}

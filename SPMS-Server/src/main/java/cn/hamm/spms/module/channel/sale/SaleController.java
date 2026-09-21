package cn.hamm.spms.module.channel.sale;

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
import cn.hamm.spms.module.channel.sale.detail.SaleDetailEntity;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailRepository;
import cn.hamm.spms.module.channel.sale.detail.SaleDetailService;
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
@Api("sale")
@Description("销售单")
@Extends(value = {Curd.Export, Curd.QueryExport}, exclude = Curd.Delete)
public class SaleController extends BaseBillController<SaleEntity, SaleService, SaleRepository, SaleDetailEntity, SaleDetailService, SaleDetailRepository> {

    /**
     * 销售单分页读出口（Mapper）；开关关闭时回退 AirPower JPA。
     */
    @Override
    @PostMapping("getPage")
    public ApiResponse<QueryPageResponse<SaleEntity>> getPage(@RequestBody QueryPageRequest<SaleEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return ApiResponse.data(service.getMapperPage(request));
    }

    /**
     * 销售单详情读出口（Mapper）；无权行不回退 JPA。
     */
    @Override
    @PostMapping("getDetail")
    public ApiResponse<SaleEntity> getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull SaleEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        SaleEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return ApiResponse.data(entity);
    }

    /**
     * 销售单总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询销售单总数")
    @PostMapping("getCount")
    public ApiResponse<Long> getCount(@RequestBody(required = false) QueryListRequest<SaleEntity> request) {
        SaleEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperCount(filter));
    }

    /**
     * 销售单统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询销售单统计")
    @PostMapping("getStat")
    public ApiResponse<ReadStat> getStat(@RequestBody(required = false) QueryListRequest<SaleEntity> request) {
        SaleEntity filter = request == null ? null : request.getFilter();
        return ApiResponse.success(service.getMapperStat(filter));
    }

    /**
     * 销售单导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public ApiResponse<String> export(@RequestBody QueryListRequest<SaleEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return ApiResponse.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}

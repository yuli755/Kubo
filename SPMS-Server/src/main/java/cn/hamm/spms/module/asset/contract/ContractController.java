package cn.hamm.spms.module.asset.contract;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.ExposeAll;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.asset.contract.participant.ParticipantEntity;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import static cn.hamm.airpower.curd.base.Curd.GetDetail;
import static cn.hamm.airpower.curd.base.Curd.GetPage;
import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;

/**
 * <h1>Controller</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；page/detail/stat/count/export 在本类显式换到 ContractMapper，
 * 让它们都经过同一条 statement 绑定和行级权限拦截链。生效/终止两个写出口保持原样。</p>
 *
 * @author Hamm.cn
 */
@Api("contract")
@Description("合同")
@Extends({Curd.Export, Curd.QueryExport})
public class ContractController extends BaseController<ContractEntity, ContractService, ContractRepository> {
    @Description("生效合同")
    @PostMapping("enforce")
    public Json enforce(@RequestBody @Validated(WhenIdRequired.class) ContractEntity contract) {
        service.enforce(contract.getId());
        return Json.success("生效合同成功");
    }

    @Description("终止合同")
    @PostMapping("stop")
    public Json stop(@RequestBody @Validated(WhenIdRequired.class) ContractEntity contract) {
        service.stop(contract.getId());
        return Json.success("终止合同成功");
    }

    /**
     * 合同分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<ContractEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 合同详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    @ExposeAll({ContractEntity.class, ParticipantEntity.class})
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull ContractEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        ContractEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        return Json.data(entity);
    }

    /**
     * 合同总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询合同总数")
    @PostMapping("getCount")
    public Json getCount(@RequestBody(required = false) QueryListRequest<ContractEntity> request) {
        ContractEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperCount(filter));
    }

    /**
     * 合同统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询合同统计")
    @PostMapping("getStat")
    public Json getStat(@RequestBody(required = false) QueryListRequest<ContractEntity> request) {
        ContractEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperStat(filter));
    }

    /**
     * 合同导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<ContractEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return Json.data(service.createMapperExportTask(request), "导出任务创建成功");
    }
}

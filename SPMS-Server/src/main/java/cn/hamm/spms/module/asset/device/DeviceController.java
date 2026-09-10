package cn.hamm.spms.module.asset.device;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.ExposeAll;
import cn.hamm.airpower.curd.annotation.Extends;
import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.permission.Permission;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.common.security.RateLimitHelper;
import cn.hamm.spms.module.iot.parameter.ParameterEntity;
import cn.hamm.spms.module.iot.report.IReportPayloadAction;
import cn.hamm.spms.module.iot.report.ReportPayload;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashSet;
import java.util.Set;

import static cn.hamm.airpower.curd.base.Curd.GetDetail;
import static cn.hamm.airpower.curd.base.Curd.GetPage;
import static cn.hamm.airpower.exception.Errors.DATA_NOT_FOUND;
import static cn.hamm.spms.common.exception.CustomError.REQUEST_TOO_FREQUENT;
import static cn.hamm.spms.module.iot.report.ReportConstant.getDeviceReportCacheKey;

/**
 * <h1>Controller</h1>
 *
 * <p>写接口继续复用 AirPower JPA CRUD；page/detail/stat/count/export 在本类显式换到 DeviceMapper，
 * 让它们都经过同一条 statement 绑定和行级权限拦截链。采集端的三个 {@code @Permission(login = false)}
 * 出口保持原样，不走权限拦截链。</p>
 *
 * @author zfy
 */
@Api("device")
@Description("设备")
@Extends({Curd.Export, Curd.QueryExport})
public class DeviceController extends BaseController<
        DeviceEntity, DeviceService, DeviceRepository
        > implements IDeviceAction, IReportPayloadAction {
    @Resource
    private RedisHelper redisHelper;

    @Resource
    private RateLimitHelper rateLimitHelper;

    /** 采集端公开出口限流 key 前缀 */
    private static final String RATE_KEY_PREFIX = "kubo:device:rate:";
    /** 采集端公开出口单 IP 每分钟最大请求数（DoS 防护阈值，刻意宽松，见 RateLimitHelper 注释） */
    private static final int MAX_REQUESTS_PER_MINUTE = 3000;

    @Override
    protected DeviceEntity afterGetDetail(@NotNull DeviceEntity device) {
        return service.getDeviceParameters(device);
    }

    /**
     * 设备分页读出口（Mapper）。
     *
     * @param request 分页与筛选
     * @return 分页结果
     */
    @Override
    @PostMapping("getPage")
    public Json getPage(@RequestBody QueryPageRequest<DeviceEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.getPage(request);
        }
        GetPage.checkApiAvailable(this);
        return Json.data(service.getMapperPage(request));
    }

    /**
     * 设备详情读出口（Mapper）。无权行不通过 JPA 回退，直接按不存在处理。
     *
     * @param source 只含 ID 的请求实体
     * @return 详情
     */
    @Override
    @PostMapping("getDetail")
    public Json getDetail(@RequestBody @Validated(WhenIdRequired.class) @NotNull DeviceEntity source) {
        if (!service.isMapperReadEnabled()) {
            return super.getDetail(source);
        }
        GetDetail.checkApiAvailable(this);
        DeviceEntity entity = service.getMapperDetail(source.getId());
        DATA_NOT_FOUND.whenNull(entity);
        entity = afterGetDetail(entity);
        return Json.data(entity);
    }

    /**
     * 设备总数读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内总数
     */
    @Description("查询设备总数")
    @PostMapping("getCount")
    public Json getCount(@RequestBody(required = false) QueryListRequest<DeviceEntity> request) {
        DeviceEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperCount(filter));
    }

    /**
     * 设备统计读出口（Mapper）。
     *
     * @param request 筛选请求
     * @return 权限范围内统计
     */
    @Description("查询设备统计")
    @PostMapping("getStat")
    public Json getStat(@RequestBody(required = false) QueryListRequest<DeviceEntity> request) {
        DeviceEntity filter = request == null ? null : request.getFilter();
        return Json.data(service.getMapperStat(filter));
    }

    /**
     * 设备导出出口：Mapper 读链路开启时走受权限拦截的取数 + 请求线程列级遮蔽；关闭时回退框架 JPA 导出。
     *
     * @param request 导出请求
     * @return 导出任务文件编码
     */
    @Override
    @Description("创建导出任务")
    @PostMapping("export")
    public Json export(@RequestBody QueryListRequest<DeviceEntity> request) {
        if (!service.isMapperReadEnabled()) {
            return super.export(request);
        }
        Curd.Export.checkApiAvailable(this);
        return Json.data(service.createMapperExportTask(request), "导出任务创建成功");
    }

    @Description("获取实时采集数据")
    @PostMapping("getCurrentReport")
    @Permission(login = false)
    @ExposeAll({ReportPayload.class})
    public Json getCurrentReport(@RequestBody @Validated(WhenIdRequired.class) DeviceEntity device,
                                 HttpServletRequest request) {
        REQUEST_TOO_FREQUENT.when(!rateLimitHelper.allowByIp(request, RATE_KEY_PREFIX, MAX_REQUESTS_PER_MINUTE, 60));
        return Json.data(service.getCurrentReport(device.getId()));
    }

    @Description("获取采集配置")
    @PostMapping("getDeviceConfig")
    @Permission(login = false)
    public Json getDeviceConfig(@RequestBody @Validated(WhenGetDeviceConfig.class) DeviceEntity device,
                                HttpServletRequest request) {
        REQUEST_TOO_FREQUENT.when(!rateLimitHelper.allowByIp(request, RATE_KEY_PREFIX, MAX_REQUESTS_PER_MINUTE, 60));
        device = service.getByUuid(device.getUuid());
        DATA_NOT_FOUND.whenNull(device);
        device.excludeNotMeta();
        Set<ParameterEntity> parameters = new HashSet<>();
        device = service.getDeviceParameters(device);
        device.getParameters().forEach(p -> {
            p.setId(null).excludeNotMeta();
            parameters.add(p);
        });
        device.setParameters(parameters);
        return Json.data(device);
    }

    @Description("获取指定设备某个参数的历史")
    @PostMapping("getDevicePayloadHistory")
    @Permission(login = false)
    public Json getDevicePayloadHistory(@RequestBody @Validated(WhenGetDevicePayloadHistory.class) ReportPayload payload,
                                        HttpServletRequest request) {
        REQUEST_TOO_FREQUENT.when(!rateLimitHelper.allowByIp(request, RATE_KEY_PREFIX, MAX_REQUESTS_PER_MINUTE, 60));
        return Json.data(service.getDevicePayloadHistory(payload));
    }

    @Override
    protected DeviceEntity beforeAppUpdate(@NotNull DeviceEntity device, @NotNull DeviceEntity exist) {
        redisHelper.delete(getDeviceReportCacheKey(device.getUuid()));
        return service.getDeviceParameters(device);
    }

    @Override
    protected DeviceEntity beforeAdd(@NotNull DeviceEntity device) {
        return service.getDeviceParameters(device);
    }
}

package cn.hamm.spms.module.asset.device;

import cn.hamm.airpower.core.CollectionUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.RandomUtil;
import cn.hamm.airpower.curd.helper.ExportHelper;
import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryListRequest;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.QueryPageResponse;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.airpower.redis.RedisHelper;
import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.common.influx.InfluxHelper;
import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.device.mapper.DeviceMapper;
import cn.hamm.spms.module.asset.device.mapper.DeviceReadShadowService;
import cn.hamm.spms.module.iot.IotServices;
import cn.hamm.spms.module.iot.parameter.ParameterEntity;
import cn.hamm.spms.module.iot.parameter.ParameterService;
import cn.hamm.spms.module.iot.report.ReportConstant;
import cn.hamm.spms.module.iot.report.ReportData;
import cn.hamm.spms.module.iot.report.ReportInfluxPayload;
import cn.hamm.spms.module.iot.report.ReportPayload;
import cn.hamm.spms.module.iot.report.enums.ReportDataType;
import cn.hamm.spms.module.iot.report.enums.ReportGranularity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionSpecification;
import cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static cn.hamm.airpower.exception.Errors.UNAUTHORIZED;
import static cn.hamm.spms.module.iot.report.ReportConstant.*;

/**
 * <h1>Service</h1>
 *
 * @author zfy
 */
@Service
public class DeviceService extends BaseService<DeviceEntity, DeviceRepository> {
    private static final int DEFAULT_READ_PAGE_SIZE = 20;
    private static final int MAX_READ_PAGE_SIZE = 200;

    /**
     * 读侧开关关闭时 Mapper Bean 根本不存在；这里必须是可选注入，才能保留 JPA 回滚路径。
     */
    @Autowired(required = false)
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceReadShadowService deviceReadShadowService;

    @Autowired
    private ExportHelper exportHelper;

    @Autowired
    private DataPermissionSpecification dataPermissionSpecification;

    /**
     * @return 是否已启用 Device 的 Mapper 读路径
     */
    public boolean isMapperReadEnabled() {
        return deviceMapper != null;
    }

    /**
     * 设备分页读出口；写路径仍走 AirPower JPA。
     *
     * @param request 分页请求
     * @return 分页结果
     */
    public @NotNull QueryPageResponse<DeviceEntity> getMapperPage(QueryPageRequest<DeviceEntity> request) {
        QueryPageRequest<DeviceEntity> normalized = request == null ? new QueryPageRequest<>() : request;
        if (!isMapperReadEnabled()) {
            return getPage(normalized);
        }
        DeviceEntity filter = normalized.getFilter() == null ? new DeviceEntity() : normalized.getFilter();
        Page requestedPage = normalized.getPage() == null ? new Page() : normalized.getPage();
        int pageNum = requestedPage.getPageNum() == null ? 1 : requestedPage.getPageNum();
        int pageSize = requestedPage.getPageSize() == null
                ? DEFAULT_READ_PAGE_SIZE : requestedPage.getPageSize();
        if (pageNum < 1 || pageSize < 1) {
            throw new IllegalArgumentException("分页页码和分页大小必须为正数");
        }
        pageSize = Math.min(pageSize, MAX_READ_PAGE_SIZE);
        long offsetValue = ((long) pageNum - 1L) * pageSize;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页页码超出可查询范围");
        }
        int offset = (int) offsetValue;
        deviceReadShadowService.submit(filter);
        List<DeviceEntity> list = deviceMapper.selectPageByPermission(filter, offset, pageSize);
        long total = deviceMapper.countByPermission(filter);
        int totalAsInt = Math.toIntExact(total);
        int pageCount = total == 0 ? 0 : Math.toIntExact((total + pageSize - 1L) / pageSize);
        Sort sort = normalized.getSort() == null ? new Sort() : normalized.getSort();
        QueryPageResponse<DeviceEntity> response = new QueryPageResponse<>();
        response.setList(list);
        response.setTotal(totalAsInt);
        response.setPageCount(pageCount);
        response.setPage(new Page().setPageNum(pageNum).setPageSize(pageSize));
        response.setSort(sort);
        return response;
    }

    /**
     * 设备详情读出口；无权行由 Mapper 返回 null。
     *
     * <p>参数列表是 {@code @ManyToMany}（无 {@code device_id} 外键），主行通过权限链后沿 JPA 关系补齐，
     * 避免为一张中间表手写 JOIN 读语句。</p>
     *
     * @param id 主键
     * @return 详情或 null
     */
    public DeviceEntity getMapperDetail(long id) {
        if (!isMapperReadEnabled()) {
            return get(id);
        }
        deviceReadShadowService.submit();
        if (deviceMapper.selectDetailByPermission(id) == null) {
            return null;
        }
        return get(id);
    }

    /**
     * 设备总数读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内行数
     */
    public long getMapperCount(DeviceEntity filter) {
        DeviceEntity normalized = filter == null ? new DeviceEntity() : filter;
        if (isMapperReadEnabled()) {
            deviceReadShadowService.submit(normalized);
            return deviceMapper.countByPermission(normalized);
        }
        return filter(normalized).size();
    }

    /**
     * 设备统计读出口。
     *
     * @param filter 业务筛选
     * @return 权限范围内统计
     */
    public ReadStat getMapperStat(DeviceEntity filter) {
        DeviceEntity normalized = filter == null ? new DeviceEntity() : filter;
        if (isMapperReadEnabled()) {
            deviceReadShadowService.submit(normalized);
            return deviceMapper.statByPermission(normalized);
        }
        List<DeviceEntity> rows = filter(normalized);
        ReadStat stat = new ReadStat();
        stat.setTotal(rows.size());
        stat.setPublishedTotal(rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsPublished())).count());
        return stat;
    }

    /**
     * 设备真实导出出口（Mapper）。
     *
     * <p>行集经 {@link DeviceMapper#selectForExport} 走同一条权限拦截链，列级遮蔽在请求线程完成后交给
     * {@link ExportHelper#createExportTask} 落盘，保留异步下载契约。</p>
     *
     * @param request 导出请求（含业务筛选）
     * @return 导出任务文件编码，供前端 {@code queryExport} 轮询下载
     */
    public String createMapperExportTask(QueryListRequest<DeviceEntity> request) {
        DeviceEntity filter = request == null || request.getFilter() == null
                ? new DeviceEntity() : request.getFilter();
        deviceReadShadowService.submit(filter);
        List<DeviceEntity> rows = deviceMapper.selectForExport(filter);
        List<DeviceEntity> masked = ExportColumnMasker.maskForExport(rows);
        return exportHelper.createExportTask(() -> {
            InputStream stream = CollectionUtil.toCsvInputStream(CsvFormulaSanitizer.sanitize(masked), DeviceEntity.class);
            return exportHelper.saveExportFileStream(stream);
        });
    }

    @Override
    protected @NotNull List<Predicate> addSearchPredicate(
            @NotNull Root<DeviceEntity> root,
            @NotNull CriteriaBuilder builder,
            @NotNull DeviceEntity search
    ) {
        List<Predicate> predicateList = new ArrayList<>();
        List<Predicate> dataPermissionPredicates = dataPermissionSpecification.buildPredicates(
                root, builder, "asset.device", DataPermissionAction.QUERY
        );
        predicateList.addAll(dataPermissionPredicates);
        return predicateList;
    }

    @Resource
    private RedisHelper redisHelper;

    @Autowired
    private InfluxHelper influxHelper;

    /**
     * 查询指定设备 UUID 的当前报告（采集端认证：必须携带匹配的设备 API-Key）
     *
     * @param deviceId 设备 ID
     * @param apiKey   采集端提交的 API-Key（请求体，不回显）
     * @return 报告列表
     */
    public List<ReportPayload> getCurrentReport(long deviceId, @Nullable String apiKey) {
        DeviceEntity device = get(deviceId);
        authenticateDevice(apiKey, device);
        Object data = redisHelper.get(ReportConstant.getDeviceReportCacheKey(device.getUuid()));
        if (Objects.isNull(data)) {
            return new ArrayList<>();
        }
        return Objects.requireNonNull(Json.parse(data.toString(), ReportData.class)).getPayloads();
    }

    /**
     * 通过 UUID 查询设备
     *
     * @param uuid UUID
     * @return 设备
     */
    public DeviceEntity getByUuid(String uuid) {
        return repository.getByUuid(uuid);
    }

    /**
     * 查询指定设备指定参数的历史记录（采集端认证：必须携带匹配的设备 API-Key）
     *
     * @param reportPayload 传入参数（含设备 UUID 与 API-Key）
     * @param apiKey        采集端提交的 API-Key
     * @return 查询历史
     */
    public List<ReportInfluxPayload> getDevicePayloadHistory(@NotNull ReportPayload reportPayload, @Nullable String apiKey) {
        DeviceEntity device = repository.getByUuid(reportPayload.getUuid());
        authenticateDevice(apiKey, device);
        ParameterEntity parameter = IotServices.getParameterService().getByCode(reportPayload.getCode());
        PARAM_INVALID.whenNull(parameter, "不支持的参数");
        ReportGranularity reportGranularity = DictionaryUtil.getDictionary(ReportGranularity.class, reportPayload.getReportGranularity());
        ReportDataType reportDataType = DictionaryUtil.getDictionary(ReportDataType.class, parameter.getDataType());
        return switch (reportDataType) {
            case NUMBER -> influxHelper.queryQuantity(reportPayload, reportGranularity);
            case STATUS -> influxHelper.queryStatus(reportPayload, reportGranularity);
            case BOOLEAN -> influxHelper.querySwitch(reportPayload, reportGranularity);
            default -> influxHelper.queryInformation(reportPayload, reportGranularity);
        };
    }

    /**
     * 设备采集端认证：常量时间比对提交的 API-Key 与库中设备密钥。
     *
     * <p>设备不存在与密钥不匹配返回同一结果（401），避免通过响应差异枚举设备是否存在。
     * 比对用 {@link MessageDigest#isEqual}（常量时间），防时序侧信道。</p>
     *
     * @param providedKey 采集端提交的 API-Key（可为空）
     * @param device      库中设备（可为空）
     */
    public void authenticateDevice(@Nullable String providedKey, @Nullable DeviceEntity device) {
        String expected = device == null ? null : device.getApiKey();
        UNAUTHORIZED.when(!apiKeyMatches(providedKey, expected), "设备认证失败");
    }

    /**
     * 常量时间比对两个 API-Key（防时序侧信道；任一为空即不匹配）。
     *
     * @param provided 采集端提交值
     * @param expected 库中期望值
     * @return 是否匹配
     */
    public static boolean apiKeyMatches(@Nullable String provided, @Nullable String expected) {
        if (provided == null || expected == null || provided.isEmpty() || expected.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 生成新的设备采集端 API-Key（Base64 编码的随机字节，与 OpenApp 的 AppSecret 同强度口径）。
     *
     * @return 新密钥
     */
    public String generateApiKey() {
        return Base64.getEncoder().encodeToString(RandomUtil.randomBytes());
    }

    /**
     * 重置指定设备的采集端 API-Key，返回新密钥（仅此一次回显，调用方负责向采集端下发）。
     *
     * @param id 设备 ID
     * @return 新密钥
     */
    public String resetApiKey(long id) {
        DeviceEntity device = get(id);
        String newKey = generateApiKey();
        device.setApiKey(newKey);
        update(device);
        return newKey;
    }

    /**
     * 获取设备的参数列表
     *
     * @param device 设备
     * @return 设备
     */
    public DeviceEntity getDeviceParameters(@NotNull DeviceEntity device) {
        Set<ParameterEntity> parameters = new HashSet<>();
        ParameterService parameterService = IotServices.getParameterService();
        if (Objects.nonNull(device.getParameters())) {
            parameters = device.getParameters().stream()
                    .map(parameter -> parameterService.get(parameter.getId()))
                    .filter(parameter -> !parameter.getIsSystem())
                    .collect(Collectors.toSet());
        }
        parameters.add(parameterService.getByCode(REPORT_KEY_OF_STATUS));
        parameters.add(parameterService.getByCode(REPORT_KEY_OF_ALARM));
        parameters.add(parameterService.getByCode(REPORT_KEY_OF_PART_COUNT));
        device.setParameters(parameters);
        return device;
    }

    @Override
    protected DeviceEntity beforeAppSaveToDatabase(@NotNull DeviceEntity device) {
        if (!StringUtils.hasText(device.getUuid())) {
            device.setUuid(device.getCode());
        }
        // 采集端 API-Key：仅在新建（无 id）且未显式指定时自动生成；更新不动已有密钥（避免每次保存重置导致采集端掉线）
        if (device.getId() == null && !StringUtils.hasText(device.getApiKey())) {
            device.setApiKey(generateApiKey());
        }
        return device;
    }
}

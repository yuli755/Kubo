package cn.hamm.spms.common.influx;

import cn.hamm.spms.common.Configs;
import cn.hamm.spms.module.iot.report.ReportConstant;
import cn.hamm.spms.module.iot.report.ReportInfluxPayload;
import cn.hamm.spms.module.iot.report.ReportPayload;
import cn.hamm.spms.module.iot.report.enums.ReportDataType;
import cn.hamm.spms.module.iot.report.enums.ReportGranularity;
import com.influxdb.LogLevel;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;
import static cn.hamm.spms.module.iot.report.enums.ReportDataType.*;
import static cn.hamm.spms.module.system.config.ConfigService.STRING_ONE;

/**
 * <h1>Influx 助手类</h1>
 *
 * @author Hamm.cn
 */
@Configuration
public class InfluxHelper {
    public static final String INFLUX_FIELD_VALUE = "value";
    private static final String INFLUX_TAG_UUID = "uuid";
    private static final String INFLUX_SQL_SPLIT = " |> ";
    private static final String INFLUX_RECORD_VALUE_KEY = "_value";

    /**
     * S-1：Flux 查询用字符串拼接（{@code getFluxQuery}），参数名/设备 ID 会被插进
     * {@code "..."} 字面量。历史查询路径已有设备 API-Key 认证前置（非法密钥拿不到
     * 查询能力），这里再加一道白名单：只允许字母数字与 {@code _ - : .}，杜绝
     * 引号闭合后的 Flux 语句注入（如 {@code x" or "1"=="1}）。
     */
    private static final Pattern FLUX_SAFE_ID = Pattern.compile("^[A-Za-z0-9_:.-]{1,128}$");

    /**
     * S-1：校验拼进 Flux 字面量的两个标识符（package-private 便于单测）。
     *
     * @param code 参数名（measurement 后缀）
     * @param uuid 设备 ID（tag 值）
     */
    static void validateFluxIdentifiers(String code, String uuid) {
        PARAM_INVALID.when(Objects.isNull(code) || !FLUX_SAFE_ID.matcher(code).matches(), "参数名包含非法字符");
        PARAM_INVALID.when(Objects.isNull(uuid) || !FLUX_SAFE_ID.matcher(uuid).matches(), "设备 ID 包含非法字符");
    }

    private InfluxDBClient influxDbClient;

    /**
     * 保存数据
     *
     * @param code  参数名
     * @param uuid  设备 ID
     * @param value 数据
     */
    public void save(String code, String uuid, Object value) {
        WriteApiBlocking writeApi = getWriteApi();
        if (Objects.nonNull(writeApi)) {
            Point point = new Point(ReportConstant.CACHE_PREFIX + code)
                    .addTag(INFLUX_TAG_UUID, uuid);
            if (value instanceof Number numberValue) {
                point.addField(INFLUX_FIELD_VALUE, numberValue);
            } else if (value instanceof Boolean booleanValue) {
                point.addField(INFLUX_FIELD_VALUE, booleanValue);
            } else if (value instanceof String stringValue) {
                point.addField(INFLUX_FIELD_VALUE, stringValue);
            } else {
                throw new RuntimeException("不支持的数据类型");
            }
            InfluxConfig influxConfig = Configs.getInfluxConfig();
            writeApi.writePoint(influxConfig.getBucket(), influxConfig.getOrg(), point);
        }
    }

    /**
     * 获取写入 API
     *
     * @return 写入 API
     */
    private @Nullable WriteApiBlocking getWriteApi() {
        try {
            initInfluxDbClient();
            // S2：未配置 InfluxDB（空令牌）时 initInfluxDbClient 不建连，client 保持 null；
            // 这里必须判空，否则下方 setLogLevel 对 null 调方法会 NPE（与「静默禁用」相矛盾）。
            if (Objects.isNull(influxDbClient)) {
                return null;
            }
            influxDbClient.setLogLevel(LogLevel.NONE);
            return influxDbClient.getWriteApiBlocking();
        } catch (Exception ignored) {
            // 建连/写点失败时关闭并置空，下次重新初始化；同样要判空，避免对 null 调用 close 二次 NPE
            if (Objects.nonNull(influxDbClient)) {
                influxDbClient.close();
                influxDbClient = null;
            }
        }
        return null;
    }

    /**
     * 查询数量
     *
     * @param payload           报告负载
     * @param reportGranularity 报告颗粒度
     * @return 数据
     */
    public List<ReportInfluxPayload> queryQuantity(ReportPayload payload, ReportGranularity reportGranularity) {
        return query(payload, NUMBER, reportGranularity);
    }

    /**
     * 查询是否开启
     *
     * @param payload           报告负载
     * @param reportGranularity 报告颗粒度
     * @return 数据
     */
    public List<ReportInfluxPayload> querySwitch(ReportPayload payload, ReportGranularity reportGranularity) {
        return query(payload, BOOLEAN, reportGranularity);
    }

    /**
     * 查询报告信息
     *
     * @param payload           报告负载
     * @param reportGranularity 报告颗粒度
     * @return 数据
     */
    public List<ReportInfluxPayload> queryInformation(ReportPayload payload, ReportGranularity reportGranularity) {
        return query(payload, STRING, reportGranularity);
    }

    /**
     * 查询报告状态
     *
     * @param payload           报告负载
     * @param reportGranularity 报告颗粒度
     * @return 数据
     */
    public List<ReportInfluxPayload> queryStatus(ReportPayload payload, ReportGranularity reportGranularity) {
        return query(payload, STATUS, reportGranularity);
    }

    /**
     * 查询报告
     *
     * @param reportPayload     报告负载
     * @param reportDataType    数据类型
     * @param reportGranularity 报告颗粒度
     * @return 数据
     */
    private @NotNull List<ReportInfluxPayload> query(ReportPayload reportPayload, ReportDataType reportDataType, ReportGranularity reportGranularity) {
        initInfluxDbClient();
        if (influxDbClient == null) {
            // S2：未配置 InfluxDB（空令牌）时静默返回空结果，不报错
            return new ArrayList<>();
        }
        influxDbClient.setLogLevel(LogLevel.BASIC);
        List<ReportInfluxPayload> result = new ArrayList<>();
        QueryApi queryApi = influxDbClient.getQueryApi();
        List<String> queryParams = getFluxQuery(reportPayload, reportDataType, reportGranularity);
        List<FluxTable> tables = queryApi.query(String.join(INFLUX_SQL_SPLIT, queryParams));
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Object value = record.getValueByKey(INFLUX_RECORD_VALUE_KEY);
                ReportInfluxPayload payload = new ReportInfluxPayload()
                        .setTimestamp(Objects.requireNonNull(record.getTime()).toEpochMilli());
                switch (reportDataType) {
                    case NUMBER:
                        payload.setValue(Objects.isNull(value) ? 0 : Double.parseDouble(value.toString()));
                        break;
                    case STRING:
                        payload.setStrValue(Objects.isNull(value) ? "" : value.toString());
                        break;
                    case BOOLEAN:
                        payload.setBoolValue(!Objects.isNull(value) && STRING_ONE.equals(value.toString()));
                        break;
                    case STATUS:
                        payload.setIntValue(Objects.isNull(value) ? 0 : Integer.parseInt(value.toString()));
                        break;
                    default:
                        continue;
                }
                result.add(payload);
            }
        }
        return result;
    }

    /**
     * 初始化 InfluxDB
     *
     * <p><b>安全（S2）</b>：令牌为空时视为「未配置」，不建立连接（上报/查询静默禁用），
     * 避免空令牌强行建连抛异常污染日志。</p>
     */
    private void initInfluxDbClient() {
        if (Objects.isNull(influxDbClient)) {
            InfluxConfig influxConfig = Configs.getInfluxConfig();
            if (influxConfig == null || influxConfig.getToken() == null || influxConfig.getToken().isBlank()) {
                return;
            }
            influxDbClient = InfluxDBClientFactory.create(
                    influxConfig.getUrl(),
                    influxConfig.getToken().toCharArray(),
                    influxConfig.getOrg(),
                    influxConfig.getBucket()
            );
        }
    }

    /**
     * 获取查询参数
     *
     * @param reportPayload     数采报告
     * @param reportDataType    数据类型
     * @param reportGranularity 报告颗粒度
     * @return 参数列表
     */
    private @NotNull List<String> getFluxQuery(@NotNull ReportPayload reportPayload, ReportDataType reportDataType, ReportGranularity reportGranularity) {
        String code = reportPayload.getCode();
        String uuid = reportPayload.getUuid();
        validateFluxIdentifiers(code, uuid);
        List<String> queryParams = new ArrayList<>();
        InfluxConfig influxConfig = Configs.getInfluxConfig();
        queryParams.add(String.format("from(bucket:\"%s\")", influxConfig.getBucket()));
        queryParams.add(String.format("range(start: %s, stop: %s)", Integer.parseInt(String.valueOf(reportPayload.getStartTime() / 1000)), Integer.parseInt(String.valueOf(reportPayload.getEndTime() / 1000))));
        queryParams.add(String.format("filter(fn: (r) => r._measurement == \"%s\" and r.uuid == \"%s\")", ReportConstant.CACHE_PREFIX + code, uuid));
        queryParams.add("filter(fn: (r) => r._field == \"value\")");
        if (Objects.requireNonNull(reportDataType) == NUMBER) {
            queryParams.add("aggregateWindow(every: " + reportGranularity.getMark() + ", fn: mean)");
            queryParams.add("fill(usePrevious: true)");
        } else {
            queryParams.add("limit(n: 500)");
        }
        return queryParams;
    }

}

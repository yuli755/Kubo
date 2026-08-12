package cn.hamm.spms.common;

/**
 * <h1>统一错误码枚举</h1>
 * <p>
 * 全平台统一错误码定义，前后端强制同步
 * 错误码格式：{模块码}{分类码}{序号}，共 6 位数字
 * 模块码：1位 (1=系统, 2=认证, 3=仓储, 4=制造, 5=财务, 6=采购, 7=销售, 8=资产, 9=人事, 0=通用)
 * 分类码：2位 (01=参数校验, 02=业务逻辑, 03=权限, 04=资源不存在, 05=并发冲突, 06=外部依赖, 07=系统错误, 08=限流熔断, 09=数据一致性)
 * 序号：3位 (001-999)
 *
 * 示例：201001 = 认证模块 + 参数校验 + 001 = 用户名或密码错误
 *
 * @author Hamm.cn
 */
public enum ErrorCode {

    // ============ 通用模块 (0xxxx) ============

    /** 成功 */
    SUCCESS(000000, "成功"),

    /** 未知错误 */
    UNKNOWN_ERROR(007001, "系统内部错误，请联系管理员"),

    /** 参数校验失败 */
    PARAM_VALIDATION_FAILED(001001, "参数校验失败: {0}"),

    /** 必填参数缺失 */
    PARAM_MISSING(001002, "必填参数 {0} 不能为空"),

    /** 参数类型错误 */
    PARAM_TYPE_MISMATCH(001003, "参数 {0} 类型错误，期望 {1}"),

    /** 参数值非法 */
    PARAM_VALUE_ILLEGAL(001004, "参数 {0} 值非法: {1}"),

    /** 请求体解析失败 */
    REQUEST_BODY_PARSE_FAILED(001005, "请求体解析失败: {0}"),

    /** 资源不存在 */
    RESOURCE_NOT_FOUND(004001, "资源不存在: {0}"),

    /** 资源已存在 */
    RESOURCE_ALREADY_EXISTS(002001, "资源已存在: {0}"),

    /** 资源已被删除 */
    RESOURCE_DELETED(004002, "资源已被删除: {0}"),

    /** 版本冲突 (乐观锁) */
    VERSION_CONFLICT(005001, "数据已被修改，请刷新后重试"),

    /** 并发冲突 */
    CONCURRENT_CONFLICT(005002, "并发操作冲突，请稍后重试"),

    /** 幂等键冲突 */
    IDEMPOTENT_CONFLICT(005003, "重复请求，请勿重复提交"),

    /** 权限不足 */
    PERMISSION_DENIED(003001, "权限不足，无法执行此操作"),

    /** 未认证 */
    UNAUTHORIZED(003002, "未登录或登录已过期，请重新登录"),

    /** Token 过期 */
    TOKEN_EXPIRED(003003, "登录凭证已过期，请重新登录"),

    /** Token 无效 */
    TOKEN_INVALID(003004, "登录凭证无效，请重新登录"),

    /** 刷新 Token 过期 */
    REFRESH_TOKEN_EXPIRED(003005, "刷新凭证已过期，请重新登录"),

    /** 刷新 Token 无效 */
    REFRESH_TOKEN_INVALID(003006, "刷新凭证无效，请重新登录"),

    /** 租户不存在 */
    TENANT_NOT_FOUND(004003, "租户不存在或已禁用"),

    /** 租户已禁用 */
    TENANT_DISABLED(003004, "租户已被禁用，请联系管理员"),

    /** 限流 */
    RATE_LIMIT_EXCEEDED(008001, "请求过于频繁，请稍后再试"),

    /** 熔断降级 */
    CIRCUIT_BREAKER_OPEN(008002, "服务暂时不可用，请稍后重试"),

    /** 服务不可用 */
    SERVICE_UNAVAILABLE(007002, "服务暂时不可用，请稍后重试"),

    /** 外部服务调用失败 */
    EXTERNAL_SERVICE_FAILED(006001, "外部服务调用失败: {0}"),

    /** 外部服务超时 */
    EXTERNAL_SERVICE_TIMEOUT(006002, "外部服务调用超时: {0}"),

    /** 数据一致性校验失败 */
    DATA_CONSISTENCY_CHECK_FAILED(009001, "数据一致性校验失败: {0}"),

    /** 数据完整性约束违反 */
    DATA_INTEGRITY_VIOLATION(009002, "数据完整性约束违反: {0}"),

    /** 文件上传失败 */
    FILE_UPLOAD_FAILED(007003, "文件上传失败: {0}"),

    /** 文件过大 */
    FILE_TOO_LARGE(001006, "文件大小超过限制: 最大允许 {0}"),

    /** 文件类型不支持 */
    FILE_TYPE_NOT_SUPPORTED(001007, "文件类型不支持: {0}"),

    /** 文件不存在 */
    FILE_NOT_FOUND(004004, "文件不存在"),

    /** 导入失败 */
    IMPORT_FAILED(007004, "数据导入失败: {0}"),

    /** 导出失败 */
    EXPORT_FAILED(007005, "数据导出失败: {0}"),

    // ============ 认证授权模块 (2xxxx) ============

    /** 用户名或密码错误 */
    AUTH_INVALID_CREDENTIALS(201001, "用户名或密码错误"),

    /** 账户被锁定 */
    AUTH_ACCOUNT_LOCKED(203001, "账户已被锁定，请联系管理员解锁"),

    /** 账户已禁用 */
    ACCOUNT_DISABLED(203002, "账户已被禁用，请联系管理员"),

    /** 密码错误次数过多 */
    PASSWORD_RETRY_LIMIT_EXCEEDED(208001, "密码错误次数过多，请 {0} 分钟后再试"),

    /** 验证码错误 */
    CAPTCHA_INVALID(201002, "验证码错误或已过期"),

    /** 验证码已过期 */
    CAPTCHA_EXPIRED(201003, "验证码已过期，请重新获取"),

    /** 登录设备数超限 */
    LOGIN_DEVICE_LIMIT_EXCEEDED(208002, "登录设备数量超限，请先在其他设备退出"),

    /** 密码过期 */
    PASSWORD_EXPIRED(202001, "密码已过期，请修改密码"),

    /** 密码强度不足 */
    PASSWORD_STRENGTH_INSUFFICIENT(201004, "密码强度不足，至少包含大小写字母、数字、特殊字符，长度 8-20 位"),

    /** 旧密码错误 */
    OLD_PASSWORD_INCORRECT(201005, "原密码错误"),

    /** 新密码与旧密码相同 */
    NEW_PASSWORD_SAME_AS_OLD(201006, "新密码不能与旧密码相同"),

    /** 两次输入密码不一致 */
    PASSWORD_MISMATCH(201007, "两次输入的密码不一致"),

    /** OAuth2 授权码无效 */
    OAUTH2_CODE_INVALID(201008, "授权码无效或已过期"),

    /** OAuth2 客户端不存在 */
    OAUTH2_CLIENT_NOT_FOUND(204001, "客户端不存在"),

    /** OAuth2 重定向 URI 不匹配 */
    OAUTH2_REDIRECT_URI_MISMATCH(201009, "重定向 URI 不匹配"),

    /** OAuth2 作用域不足 */
    OAUTH2_INSUFFICIENT_SCOPE(203005, "授权范围不足"),

    /** 角色不存在 */
    ROLE_NOT_FOUND(204002, "角色不存在"),

    /** 角色已存在 */
    ROLE_ALREADY_EXISTS(202002, "角色标识已存在"),

    /** 角色被引用，不可删除 */
    ROLE_IN_USE(202003, "角色正在被使用，不可删除"),

    /** 权限不存在 */
    PERMISSION_NOT_FOUND(204003, "权限不存在"),

    /** 权限已存在 */
    PERMISSION_ALREADY_EXISTS(202004, "权限标识已存在"),

    /** 系统权限不可修改 */
    SYSTEM_PERMISSION_IMMUTABLE(203006, "系统内置权限不可修改"),

    /** 用户不存在 */
    USER_NOT_FOUND(204004, "用户不存在"),

    /** 用户名已存在 */
    USERNAME_ALREADY_EXISTS(202005, "用户名已存在"),

    /** 手机号已绑定 */
    PHONE_ALREADY_BOUND(202006, "手机号已被其他账号绑定"),

    /** 邮箱已绑定 */
    EMAIL_ALREADY_BOUND(202007, "邮箱已被其他账号绑定"),

    // ============ 系统管理模块 (1xxxx) ============

    /** 菜单不存在 */
    MENU_NOT_FOUND(104001, "菜单不存在"),

    /** 菜单标识已存在 */
    MENU_CODE_ALREADY_EXISTS(102001, "菜单标识已存在"),

    /** 部门不存在 */
    DEPARTMENT_NOT_FOUND(104002, "部门不存在"),

    /** 部门名称已存在 */
    DEPARTMENT_NAME_EXISTS(102002, "部门名称已存在"),

    /** 部门下存在子部门，不可删除 */
    DEPARTMENT_HAS_CHILDREN(102002, "部门下存在子部门，不可删除"),

    /** 部门下存在用户，不可删除 */
    DEPARTMENT_HAS_USERS(102003, "部门下存在用户，不可删除"),

    /** 字典不存在 */
    DICTIONARY_NOT_FOUND(104003, "字典不存在"),

    /** 字典编码已存在 */
    DICTIONARY_CODE_EXISTS(102003, "字典编码已存在"),

    /** 系统配置不存在 */
    CONFIG_NOT_FOUND(104004, "系统配置不存在"),

    /** 系统配置只读，不可修改 */
    CONFIG_READ_ONLY(103001, "系统内置配置只读，不可修改"),

    // ============ 仓储管理模块 (3xxxx) ============

    /** 库存不足 */
    INSUFFICIENT_STOCK(302001, "库存不足: {0} 当前可用 {1}, 需求 {2}"),

    /** 库存已被锁定 */
    STOCK_LOCKED(302002, "库存已被锁定，请稍后重试"),

    /** 库存冻结 */
    STOCK_FROZEN(302003, "库存已冻结，不可操作"),

    /** 仓库不存在 */
    WAREHOUSE_NOT_FOUND(304001, "仓库不存在"),

    /** 仓库编码已存在 */
    WAREHOUSE_CODE_EXISTS(302003, "仓库编码已存在"),

    /** 货位不存在 */
    LOCATION_NOT_FOUND(304002, "货位不存在"),

    /** 货位已被占用 */
    LOCATION_OCCUPIED(302004, "货位已被占用"),

    /** 物料不存在 */
    MATERIAL_NOT_FOUND(304003, "物料不存在"),

    /** 物料编码已存在 */
    MATERIAL_CODE_EXISTS(302004, "物料编码已存在"),

    /** 物料已被引用，不可删除 */
    MATERIAL_IN_USE(302005, "物料正在被使用，不可删除"),

    /** 入库单不存在 */
    INBOUND_ORDER_NOT_FOUND(304004, "入库单不存在"),

    /** 入库单已提交，不可修改 */
    INBOUND_ORDER_SUBMITTED(302005, "入库单已提交，不可修改"),

    /** 入库单已完成，不可撤销 */
    INBOUND_ORDER_COMPLETED(302006, "入库单已完成，不可撤销"),

    /** 出库单不存在 */
    OUTBOUND_ORDER_NOT_FOUND(304005, "出库单不存在"),

    /** 出库单已提交，不可修改 */
    OUTBOUND_ORDER_SUBMITTED(302007, "出库单已提交，不可修改"),

    /** 出库单已完成，不可撤销 */
    OUTBOUND_ORDER_COMPLETED(302008, "出库单已完成，不可撤销"),

    /** 盘点单不存在 */
    INVENTORY_CHECK_NOT_FOUND(304006, "盘点单不存在"),

    /** 盘点单已开始，不可修改 */
    INVENTORY_CHECK_STARTED(302009, "盘点单已开始，不可修改"),

    /** 盘点单已完成，不可修改 */
    INVENTORY_CHECK_COMPLETED(302010, "盘点单已完成，不可修改"),

    /** 调拨单不存在 */
    TRANSFER_ORDER_NOT_FOUND(304007, "调拨单不存在"),

    /** 调拨单源仓库与目标仓库相同 */
    TRANSFER_SAME_WAREHOUSE(301001, "源仓库与目标仓库不能相同"),

    // ============ 制造执行模块 (4xxxx) ============

    /** 工单不存在 */
    WORK_ORDER_NOT_FOUND(404001, "工单不存在"),

    /** 工单编号已存在 */
    WORK_ORDER_CODE_EXISTS(402001, "工单编号已存在"),

    /** 工单状态不可变更 */
    WORK_ORDER_STATUS_IMMUTABLE(402002, "当前状态下不可执行此操作"),

    /** 工单已发布，不可修改 */
    WORK_ORDER_PUBLISHED(402003, "工单已发布，不可修改"),

    /** 工单已完成，不可操作 */
    WORK_ORDER_COMPLETED(402004, "工单已完成，不可操作"),

    /** 工单已关闭，不可操作 */
    WORK_ORDER_CLOSED(402005, "工单已关闭，不可操作"),

    /** 工序不存在 */
    OPERATION_NOT_FOUND(404002, "工序不存在"),

    /** 工序顺序冲突 */
    OPERATION_SEQUENCE_CONFLICT(402002, "工序顺序冲突"),

    /** 工序已开始，不可修改 */
    OPERATION_STARTED(402003, "工序已开始，不可修改"),

    /** 工序已完成，不可操作 */
    OPERATION_COMPLETED(402004, "工序已完成，不可操作"),

    /** BOM 不存在 */
    BOM_NOT_FOUND(404003, "BOM 不存在"),

    /** BOM 版本冲突 */
    BOM_VERSION_CONFLICT(402004, "BOM 版本冲突"),

    /** 工艺路线不存在 */
    ROUTING_NOT_FOUND(404004, "工艺路线不存在"),

    /** 设备不存在 */
    EQUIPMENT_NOT_FOUND(404005, "设备不存在"),

    /** 设备编码已存在 */
    EQUIPMENT_CODE_EXISTS(402005, "设备编码已存在"),

    /** 设备状态异常 */
    EQUIPMENT_STATUS_ABNORMAL(402006, "设备状态异常，不可执行操作"),

    /** 设备维护中 */
    EQUIPMENT_UNDER_MAINTENANCE(402007, "设备维护中，不可使用"),

    /** 质检单不存在 */
    QUALITY_CHECK_NOT_FOUND(404006, "质检单不存在"),

    /** 质检不合格 */
    QUALITY_CHECK_FAILED(402008, "质检不合格"),

    // ============ 财务核算模块 (5xxxx) ============

    /** 凭证不存在 */
    VOUCHER_NOT_FOUND(504001, "凭证不存在"),

    /** 凭证编号已存在 */
    VOUCHER_CODE_EXISTS(502001, "凭证编号已存在"),

    /** 凭证已记账，不可修改 */
    VOUCHER_POSTED(502002, "凭证已记账，不可修改"),

    /** 凭证借贷不平衡 */
    VOUCHER_UNBALANCED(502003, "凭证借贷方金额不平衡"),

    /** 会计科目不存在 */
    ACCOUNT_NOT_FOUND(504002, "会计科目不存在"),

    /** 会计科目编码已存在 */
    ACCOUNT_CODE_EXISTS(502004, "会计科目编码已存在"),

    /** 会计科目被引用，不可删除 */
    ACCOUNT_IN_USE(502005, "会计科目正在被使用，不可删除"),

    /** 期间已锁账 */
    PERIOD_LOCKED(502006, "会计期间已锁账，不可记账"),

    /** 期间已结账 */
    PERIOD_CLOSED(502007, "会计期间已结账，不可操作"),

    /** 成本核算单不存在 */
    COST_CALCULATION_NOT_FOUND(504003, "成本核算单不存在"),

    /** 成本核算已锁定 */
    COST_CALCULATION_LOCKED(502008, "成本核算已锁定，不可修改"),

    /** 成本差异超阈值 */
    COST_VARIANCE_EXCEEDED(502009, "成本差异超出阈值: {0}%"),

    // ============ 采购管理模块 (6xxxx) ============

    /** 采购订单不存在 */
    PURCHASE_ORDER_NOT_FOUND(604001, "采购订单不存在"),

    /** 采购订单编号已存在 */
    PURCHASE_ORDER_CODE_EXISTS(602001, "采购订单编号已存在"),

    /** 采购订单已审批，不可修改 */
    PURCHASE_ORDER_APPROVED(602002, "采购订单已审批，不可修改"),

    /** 采购订单已完成，不可操作 */
    PURCHASE_ORDER_COMPLETED(602003, "采购订单已完成，不可操作"),

    /** 供应商不存在 */
    SUPPLIER_NOT_FOUND(604002, "供应商不存在"),

    /** 供应商编码已存在 */
    SUPPLIER_CODE_EXISTS(602004, "供应商编码已存在"),

    /** 供应商已禁用 */
    SUPPLIER_DISABLED(602005, "供应商已禁用，不可下单"),

    /** 到货单不存在 */
    RECEIPT_ORDER_NOT_FOUND(604003, "到货单不存在"),

    /** 到货数量超订单数量 */
    RECEIPT_QTY_EXCEEDS_ORDER(602005, "到货数量不能超过订单数量"),

    // ============ 销售管理模块 (7xxxx) ============

    /** 销售订单不存在 */
    SALES_ORDER_NOT_FOUND(704001, "销售订单不存在"),

    /** 销售订单编号已存在 */
    SALES_ORDER_CODE_EXISTS(702001, "销售订单编号已存在"),

    /** 销售订单已审批，不可修改 */
    SALES_ORDER_APPROVED(702002, "销售订单已审批，不可修改"),

    /** 销售订单已发货，不可修改 */
    SALES_ORDER_SHIPPED(702003, "销售订单已发货，不可修改"),

    /** 客户不存在 */
    CUSTOMER_NOT_FOUND(704002, "客户不存在"),

    /** 客户编码已存在 */
    CUSTOMER_CODE_EXISTS(702004, "客户编码已存在"),

    /** 客户信用额度不足 */
    CUSTOMER_CREDIT_INSUFFICIENT(702005, "客户信用额度不足，剩余额度: {0}"),

    /** 发货单不存在 */
    DELIVERY_ORDER_NOT_FOUND(704003, "发货单不存在"),

    /** 发货数量超订单数量 */
    DELIVERY_QTY_EXCEEDS_ORDER(702006, "发货数量不能超过订单数量"),

    /** 发票不存在 */
    INVOICE_NOT_FOUND(704004, "发票不存在"),

    /** 发票已开具，不可修改 */
    INVOICE_ISSUED(702007, "发票已开具，不可修改"),

    // ============ 资产设备模块 (8xxxx) ============

    /** 资产不存在 */
    ASSET_NOT_FOUND(804001, "资产不存在"),

    /** 资产编码已存在 */
    ASSET_CODE_EXISTS(802001, "资产编码已存在"),

    /** 资产状态异常 */
    ASSET_STATUS_ABNORMAL(802002, "资产状态异常"),

    /** 维修工单不存在 */
    MAINTENANCE_ORDER_NOT_FOUND(804002, "维修工单不存在"),

    /** 维修工单已派单，不可修改 */
    MAINTENANCE_ORDER_DISPATCHED(802003, "维修工单已派单，不可修改"),

    /** 点检计划不存在 */
    INSPECTION_PLAN_NOT_FOUND(804003, "点检计划不存在"),

    /** 备件不存在 */
    SPARE_PART_NOT_FOUND(804004, "备件不存在"),

    /** 备件库存不足 */
    SPARE_PART_INSUFFICIENT(802004, "备件库存不足"),

    // ============ 人事管理模块 (9xxxx) ============

    /** 员工不存在 */
    EMPLOYEE_NOT_FOUND(904001, "员工不存在"),

    /** 员工工号已存在 */
    EMPLOYEE_CODE_EXISTS(902001, "员工工号已存在"),

    /** 员工身份证号已存在 */
    EMPLOYEE_ID_CARD_EXISTS(902002, "身份证号已存在"),

    /** 员工手机号已存在 */
    EMPLOYEE_PHONE_EXISTS(902003, "手机号已存在"),

    /** 员工邮箱已存在 */
    EMPLOYEE_EMAIL_EXISTS(902004, "邮箱已存在"),

    /** 员工在职中，不可删除 */
    EMPLOYEE_ACTIVE(902005, "员工在职中，不可删除"),

    /** 考勤记录不存在 */
    ATTENDANCE_NOT_FOUND(904002, "考勤记录不存在"),

    /** 考勤已打卡 */
    ATTENDANCE_ALREADY_PUNCHED(902006, "已打卡，请勿重复操作"),

    /** 考勤异常 */
    ATTENDANCE_ABNORMAL(902007, "考勤异常: {0}"),

    /** 薪资单不存在 */
    PAYROLL_NOT_FOUND(904003, "薪资单不存在"),

    /** 薪资已发放，不可修改 */
    PAYROLL_ISSUED(902008, "薪资已发放，不可修改"),

    // ============ 物联网模块 (0xxxx 扩展) ============

    /** 设备不在线 */
    DEVICE_OFFLINE(004005, "设备不在线"),

    /** 设备协议解析失败 */
    DEVICE_PROTOCOL_PARSE_FAILED(007004, "设备协议解析失败: {0}"),

    /** 设备指令下发失败 */
    DEVICE_COMMAND_FAILED(007005, "设备指令下发失败: {0}"),

    /** 设备数据上报异常 */
    DEVICE_DATA_ABNORMAL(002008, "设备数据异常: {0}"),

    /** 规则引擎执行失败 */
    RULE_ENGINE_EXECUTION_FAILED(007006, "规则引擎执行失败: {0}"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Object... args) {
        return String.format(message, args);
    }

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }

    /**
     * 判断是否为业务异常 (非系统错误)
     */
    public boolean isBusinessError() {
        return code >= 100000 && code < 700000; // 业务模块错误码范围
    }

    /**
     * 判断是否为系统错误
     */
    public boolean isSystemError() {
        return code >= 700000 && code < 800000;
    }

    /**
     * 判断是否为客户端错误 (4xx 类)
     */
    public boolean isClientError() {
        int category = (code / 1000) % 100;
        return category >= 1 && category <= 4;
    }

    /**
     * 判断是否为服务端错误 (5xx 类)
     */
    public boolean isServerError() {
        int category = (code / 1000) % 100;
        return category >= 5 && category <= 9;
    }
}
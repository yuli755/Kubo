/**
 * Kubo 统一错误码枚举 (TypeScript 版本)
 *
 * 此文件与后端 ErrorCode.java 强制同步
 * 运行 `npm run sync:error-codes` 可从后端自动生成
 *
 * 错误码格式：{模块码}{分类码}{序号}，共 6 位数字
 * 模块码：1位 (1=系统, 2=认证, 3=仓储, 4=制造, 5=财务, 6=采购, 7=销售, 8=资产, 9=人事, 0=通用)
 * 分类码：2位 (01=参数校验, 02=业务逻辑, 03=权限, 04=资源不存在, 05=并发冲突, 06=外部依赖, 07=系统错误, 08=限流熔断, 09=数据一致性)
 * 序号：3位 (001-999)
 *
 * 示例：201001 = 认证模块 + 参数校验 + 001 = 用户名或密码错误
 *
 * @author Hamm.cn
 * @version 1.0.0
 * @sync-from ErrorCode.java
 */

// ============ 核心类型定义 ============

/** 错误码基础接口 */
export interface ErrorCodeDefinition {
  code: number;
  message: string;
  module: string;
  category: string;
  description: string;
}

/** 错误码分类枚举 */
export enum ErrorCategory {
  VALIDATION = 'VALIDATION',           // 01 - 参数校验
  BUSINESS = 'BUSINESS',               // 02 - 业务逻辑
  PERMISSION = 'PERMISSION',           // 03 - 权限
  NOT_FOUND = 'NOT_FOUND',             // 04 - 资源不存在
  CONCURRENCY = 'CONCURRENCY',         // 05 - 并发冲突
  EXTERNAL = 'EXTERNAL',               // 06 - 外部依赖
  SYSTEM = 'SYSTEM',                   // 07 - 系统错误
  RATE_LIMIT = 'RATE_LIMIT',           // 08 - 限流熔断
  DATA_CONSISTENCY = 'DATA_CONSISTENCY' // 09 - 数据一致性
}

/** 错误码模块枚举 */
export enum ErrorModule {
  COMMON = 'COMMON',        // 0 - 通用
  SYSTEM = 'SYSTEM',        // 1 - 系统管理
  AUTH = 'AUTH',            // 2 - 认证授权
  WMS = 'WMS',              // 3 - 仓储管理
  MES = 'MES',              // 4 - 制造执行
  FINANCE = 'FINANCE',      // 5 - 财务核算
  PROCUREMENT = 'PROCUREMENT', // 6 - 采购管理
  SALES = 'SALES',          // 7 - 销售管理
  ASSET = 'ASSET',          // 8 - 资产设备
  PERSONNEL = 'PERSONNEL',  // 9 - 人事管理
  IOT = 'IOT'               // 0 扩展 - 物联网
}

/** HTTP 状态码映射 */
export enum HttpStatus {
  OK = 200,
  CREATED = 201,
  NO_CONTENT = 204,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  CONFLICT = 409,
  UNPROCESSABLE_ENTITY = 422,
  TOO_MANY_REQUESTS = 429,
  INTERNAL_SERVER_ERROR = 500,
  SERVICE_UNAVAILABLE = 503
}

// ============ 错误码枚举定义 ============

/**
 * 统一错误码枚举
 * 与后端 ErrorCode.java 强制同步
 */
export enum ErrorCode {
  // ============ 通用模块 (0xxxx) ============

  /** 成功 */
  SUCCESS = 0,

  /** 未知错误 */
  UNKNOWN_ERROR = 7001,

  /** 参数校验失败 */
  PARAM_VALIDATION_FAILED = 1001,

  /** 必填参数缺失 */
  PARAM_MISSING = 1002,

  /** 参数类型错误 */
  PARAM_TYPE_MISMATCH = 1003,

  /** 参数值非法 */
  PARAM_VALUE_ILLEGAL = 1004,

  /** 请求体解析失败 */
  REQUEST_BODY_PARSE_FAILED = 1005,

  /** 资源不存在 */
  RESOURCE_NOT_FOUND = 4001,

  /** 资源已存在 */
  RESOURCE_ALREADY_EXISTS = 2001,

  /** 资源已被删除 */
  RESOURCE_DELETED = 4002,

  /** 版本冲突 (乐观锁) */
  VERSION_CONFLICT = 5001,

  /** 并发冲突 */
  CONCURRENT_CONFLICT = 5002,

  /** 幂等键冲突 */
  IDEMPOTENT_CONFLICT = 5003,

  /** 权限不足 */
  PERMISSION_DENIED = 3001,

  /** 未认证 */
  UNAUTHORIZED = 3002,

  /** Token 过期 */
  TOKEN_EXPIRED = 3003,

  /** Token 无效 */
  TOKEN_INVALID = 3004,

  /** 刷新 Token 过期 */
  REFRESH_TOKEN_EXPIRED = 3005,

  /** 刷新 Token 无效 */
  REFRESH_TOKEN_INVALID = 3006,

  /** 租户不存在 */
  TENANT_NOT_FOUND = 4003,

  /** 租户已禁用 */
  TENANT_DISABLED = 3004,

  /** 限流 */
  RATE_LIMIT_EXCEEDED = 8001,

  /** 熔断降级 */
  CIRCUIT_BREAKER_OPEN = 8002,

  /** 服务不可用 */
  SERVICE_UNAVAILABLE = 7002,

  /** 外部服务调用失败 */
  EXTERNAL_SERVICE_FAILED = 6001,

  /** 外部服务超时 */
  EXTERNAL_SERVICE_TIMEOUT = 6002,

  /** 数据一致性校验失败 */
  DATA_CONSISTENCY_CHECK_FAILED = 9001,

  /** 数据完整性约束违反 */
  DATA_INTEGRITY_VIOLATION = 9002,

  /** 文件上传失败 */
  FILE_UPLOAD_FAILED = 7003,

  /** 文件过大 */
  FILE_TOO_LARGE = 1006,

  /** 文件类型不支持 */
  FILE_TYPE_NOT_SUPPORTED = 1007,

  /** 文件不存在 */
  FILE_NOT_FOUND = 4004,

  /** 导入失败 */
  IMPORT_FAILED = 7004,

  /** 导出失败 */
  EXPORT_FAILED = 7005,

  // ============ 认证授权模块 (2xxxx) ============

  /** 用户名或密码错误 */
  AUTH_INVALID_CREDENTIALS = 201001,

  /** 账户被锁定 */
  AUTH_ACCOUNT_LOCKED = 203001,

  /** 账户已禁用 */
  ACCOUNT_DISABLED = 203002,

  /** 密码错误次数过多 */
  PASSWORD_RETRY_LIMIT_EXCEEDED = 208001,

  /** 验证码错误 */
  CAPTCHA_INVALID = 201002,

  /** 验证码已过期 */
  CAPTCHA_EXPIRED = 201003,

  /** 登录设备数超限 */
  LOGIN_DEVICE_LIMIT_EXCEEDED = 208002,

  /** 密码过期 */
  PASSWORD_EXPIRED = 202001,

  /** 密码强度不足 */
  PASSWORD_STRENGTH_INSUFFICIENT = 201004,

  /** 旧密码错误 */
  OLD_PASSWORD_INCORRECT = 201005,

  /** 新密码与旧密码相同 */
  NEW_PASSWORD_SAME_AS_OLD = 201006,

  /** 两次输入密码不一致 */
  PASSWORD_MISMATCH = 201007,

  /** OAuth2 授权码无效 */
  OAUTH2_CODE_INVALID = 201008,

  /** OAuth2 客户端不存在 */
  OAUTH2_CLIENT_NOT_FOUND = 204001,

  /** OAuth2 重定向 URI 不匹配 */
  OAUTH2_REDIRECT_URI_MISMATCH = 201009,

  /** OAuth2 作用域不足 */
  OAUTH2_INSUFFICIENT_SCOPE = 203005,

  /** 角色不存在 */
  ROLE_NOT_FOUND = 204002,

  /** 角色已存在 */
  ROLE_ALREADY_EXISTS = 202002,

  /** 角色被引用，不可删除 */
  ROLE_IN_USE = 202003,

  /** 权限不存在 */
  PERMISSION_NOT_FOUND = 204003,

  /** 权限已存在 */
  PERMISSION_ALREADY_EXISTS = 202004,

  /** 系统权限不可修改 */
  SYSTEM_PERMISSION_IMMUTABLE = 203006,

  /** 用户不存在 */
  USER_NOT_FOUND = 204004,

  /** 用户名已存在 */
  USERNAME_ALREADY_EXISTS = 202005,

  /** 手机号已绑定 */
  PHONE_ALREADY_BOUND = 202006,

  /** 邮箱已绑定 */
  EMAIL_ALREADY_BOUND = 202007,

  // ============ 系统管理模块 (1xxxx) ============

  /** 菜单不存在 */
  MENU_NOT_FOUND = 104001,

  /** 菜单标识已存在 */
  MENU_CODE_ALREADY_EXISTS = 102001,

  /** 部门不存在 */
  DEPARTMENT_NOT_FOUND = 104002,

  /** 部门名称已存在 */
  DEPARTMENT_NAME_EXISTS = 102002,

  /** 部门下存在子部门，不可删除 */
  DEPARTMENT_HAS_CHILDREN = 102002,

  /** 部门下存在用户，不可删除 */
  DEPARTMENT_HAS_USERS = 102003,

  /** 字典不存在 */
  DICTIONARY_NOT_FOUND = 104003,

  /** 字典编码已存在 */
  DICTIONARY_CODE_EXISTS = 102003,

  /** 系统配置不存在 */
  CONFIG_NOT_FOUND = 104004,

  /** 系统配置只读，不可修改 */
  CONFIG_READ_ONLY = 103001,

  // ============ 仓储管理模块 (3xxxx) ============

  /** 库存不足 */
  INSUFFICIENT_STOCK = 302001,

  /** 库存已被锁定 */
  STOCK_LOCKED = 302002,

  /** 库存冻结 */
  STOCK_FROZEN = 302003,

  /** 仓库不存在 */
  WAREHOUSE_NOT_FOUND = 304001,

  /** 仓库编码已存在 */
  WAREHOUSE_CODE_EXISTS = 302003,

  /** 货位不存在 */
  LOCATION_NOT_FOUND = 304002,

  /** 货位已被占用 */
  LOCATION_OCCUPIED = 302004,

  /** 物料不存在 */
  MATERIAL_NOT_FOUND = 304003,

  /** 物料编码已存在 */
  MATERIAL_CODE_EXISTS = 302004,

  /** 物料已被引用，不可删除 */
  MATERIAL_IN_USE = 302005,

  /** 入库单不存在 */
  INBOUND_ORDER_NOT_FOUND = 304004,

  /** 入库单已提交，不可修改 */
  INBOUND_ORDER_SUBMITTED = 302005,

  /** 入库单已完成，不可撤销 */
  INBOUND_ORDER_COMPLETED = 302006,

  /** 出库单不存在 */
  OUTBOUND_ORDER_NOT_FOUND = 304005,

  /** 出库单已提交，不可修改 */
  OUTBOUND_ORDER_SUBMITTED = 302007,

  /** 出库单已完成，不可撤销 */
  OUTBOUND_ORDER_COMPLETED = 302008,

  /** 盘点单不存在 */
  INVENTORY_CHECK_NOT_FOUND = 304006,

  /** 盘点单已开始，不可修改 */
  INVENTORY_CHECK_STARTED = 302009,

  /** 盘点单已完成，不可修改 */
  INVENTORY_CHECK_COMPLETED = 302010,

  /** 调拨单不存在 */
  TRANSFER_ORDER_NOT_FOUND = 304007,

  /** 调拨单源仓库与目标仓库相同 */
  TRANSFER_SAME_WAREHOUSE = 301001,

  // ============ 制造执行模块 (4xxxx) ============

  /** 工单不存在 */
  WORK_ORDER_NOT_FOUND = 404001,

  /** 工单编号已存在 */
  WORK_ORDER_CODE_EXISTS = 402001,

  /** 工单状态不可变更 */
  WORK_ORDER_STATUS_IMMUTABLE = 402002,

  /** 工单已发布，不可修改 */
  WORK_ORDER_PUBLISHED = 402003,

  /** 工单已完成，不可操作 */
  WORK_ORDER_COMPLETED = 402004,

  /** 工单已关闭，不可操作 */
  WORK_ORDER_CLOSED = 402005,

  /** 工序不存在 */
  OPERATION_NOT_FOUND = 404002,

  /** 工序顺序冲突 */
  OPERATION_SEQUENCE_CONFLICT = 402002,

  /** 工序已开始，不可修改 */
  OPERATION_STARTED = 402003,

  /** 工序已完成，不可操作 */
  OPERATION_COMPLETED = 402004,

  /** BOM 不存在 */
  BOM_NOT_FOUND = 404003,

  /** BOM 版本冲突 */
  BOM_VERSION_CONFLICT = 402004,

  /** 工艺路线不存在 */
  ROUTING_NOT_FOUND = 404004,

  /** 设备不存在 */
  EQUIPMENT_NOT_FOUND = 404005,

  /** 设备编码已存在 */
  EQUIPMENT_CODE_EXISTS = 402005,

  /** 设备状态异常 */
  EQUIPMENT_STATUS_ABNORMAL = 402006,

  /** 设备维护中 */
  EQUIPMENT_UNDER_MAINTENANCE = 402007,

  /** 质检单不存在 */
  QUALITY_CHECK_NOT_FOUND = 404006,

  /** 质检不合格 */
  QUALITY_CHECK_FAILED = 402008,

  // ============ 财务核算模块 (5xxxx) ============

  /** 凭证不存在 */
  VOUCHER_NOT_FOUND = 504001,

  /** 凭证编号已存在 */
  VOUCHER_CODE_EXISTS = 502001,

  /** 凭证已记账，不可修改 */
  VOUCHER_POSTED = 502002,

  /** 凭证借贷不平衡 */
  VOUCHER_UNBALANCED = 502003,

  /** 会计科目不存在 */
  ACCOUNT_NOT_FOUND = 504002,

  /** 会计科目编码已存在 */
  ACCOUNT_CODE_EXISTS = 502004,

  /** 会计科目被引用，不可删除 */
  ACCOUNT_IN_USE = 502005,

  /** 期间已锁账 */
  PERIOD_LOCKED = 502006,

  /** 期间已结账 */
  PERIOD_CLOSED = 502007,

  /** 成本核算单不存在 */
  COST_CALCULATION_NOT_FOUND = 504003,

  /** 成本核算已锁定 */
  COST_CALCULATION_LOCKED = 502008,

  /** 成本差异超阈值 */
  COST_VARIANCE_EXCEEDED = 502009,

  // ============ 采购管理模块 (6xxxx) ============

  /** 采购订单不存在 */
  PURCHASE_ORDER_NOT_FOUND = 604001,

  /** 采购订单编号已存在 */
  PURCHASE_ORDER_CODE_EXISTS = 602001,

  /** 采购订单已审批，不可修改 */
  PURCHASE_ORDER_APPROVED = 602002,

  /** 采购订单已完成，不可操作 */
  PURCHASE_ORDER_COMPLETED = 602003,

  /** 供应商不存在 */
  SUPPLIER_NOT_FOUND = 604002,

  /** 供应商编码已存在 */
  SUPPLIER_CODE_EXISTS = 602004,

  /** 供应商已禁用 */
  SUPPLIER_DISABLED = 602005,

  /** 到货单不存在 */
  RECEIPT_ORDER_NOT_FOUND = 604003,

  /** 到货数量超订单数量 */
  RECEIPT_QTY_EXCEEDS_ORDER = 602005,

  // ============ 销售管理模块 (7xxxx) ============

  /** 销售订单不存在 */
  SALES_ORDER_NOT_FOUND = 704001,

  /** 销售订单编号已存在 */
  SALES_ORDER_CODE_EXISTS = 702001,

  /** 销售订单已审批，不可修改 */
  SALES_ORDER_APPROVED = 702002,

  /** 销售订单已发货，不可修改 */
  SALES_ORDER_SHIPPED = 702003,

  /** 客户不存在 */
  CUSTOMER_NOT_FOUND = 704002,

  /** 客户编码已存在 */
  CUSTOMER_CODE_EXISTS = 702004,

  /** 客户信用额度不足 */
  CUSTOMER_CREDIT_INSUFFICIENT = 702005,

  /** 发货单不存在 */
  DELIVERY_ORDER_NOT_FOUND = 704003,

  /** 发货数量超订单数量 */
  DELIVERY_QTY_EXCEEDS_ORDER = 702006,

  /** 发票不存在 */
  INVOICE_NOT_FOUND = 704004,

  /** 发票已开具，不可修改 */
  INVOICE_ISSUED = 702007,

  // ============ 资产设备模块 (8xxxx) ============

  /** 资产不存在 */
  ASSET_NOT_FOUND = 804001,

  /** 资产编码已存在 */
  ASSET_CODE_EXISTS = 802001,

  /** 资产状态异常 */
  ASSET_STATUS_ABNORMAL = 802002,

  /** 维修工单不存在 */
  MAINTENANCE_ORDER_NOT_FOUND = 804002,

  /** 维修工单已派单，不可修改 */
  MAINTENANCE_ORDER_DISPATCHED = 802003,

  /** 点检计划不存在 */
  INSPECTION_PLAN_NOT_FOUND = 804003,

  /** 备件不存在 */
  SPARE_PART_NOT_FOUND = 804004,

  /** 备件库存不足 */
  SPARE_PART_INSUFFICIENT = 802004,

  // ============ 人事管理模块 (9xxxx) ============

  /** 员工不存在 */
  EMPLOYEE_NOT_FOUND = 904001,

  /** 员工工号已存在 */
  EMPLOYEE_CODE_EXISTS = 902001,

  /** 员工身份证号已存在 */
  EMPLOYEE_ID_CARD_EXISTS = 902002,

  /** 员工手机号已存在 */
  EMPLOYEE_PHONE_EXISTS = 902003,

  /** 员工邮箱已存在 */
  EMPLOYEE_EMAIL_EXISTS = 902004,

  /** 员工在职中，不可删除 */
  EMPLOYEE_ACTIVE = 902005,

  /** 考勤记录不存在 */
  ATTENDANCE_NOT_FOUND = 904002,

  /** 考勤已打卡 */
  ATTENDANCE_ALREADY_PUNCHED = 902006,

  /** 考勤异常 */
  ATTENDANCE_ABNORMAL = 902007,

  /** 薪资单不存在 */
  PAYROLL_NOT_FOUND = 904003,

  /** 薪资已发放，不可修改 */
  PAYROLL_ISSUED = 902008,

  // ============ 物联网模块 (0xxxx 扩展) ============

  /** 设备不在线 */
  DEVICE_OFFLINE = 4005,

  /** 设备协议解析失败 */
  DEVICE_PROTOCOL_PARSE_FAILED = 7004,

  /** 设备指令下发失败 */
  DEVICE_COMMAND_FAILED = 7005,

  /** 设备数据上报异常 */
  DEVICE_DATA_ABNORMAL = 2008,

  /** 规则引擎执行失败 */
  RULE_ENGINE_EXECUTION_FAILED = 7006,
}

// ============ 工具函数 ============

/** 错误码元数据映射 */
export interface ErrorCodeMeta {
  code: ErrorCode;
  httpStatus: HttpStatus;
  category: ErrorCategory;
  module: ErrorModule;
  message: string;
  description: string;
}

/** 错误码元数据映射表 */
export const ERROR_CODE_META: Record<ErrorCode, ErrorCodeMeta> = {
  [ErrorCode.SUCCESS]: {
    code: ErrorCode.SUCCESS,
    httpStatus: HttpStatus.OK,
    category: ErrorCategory.BUSINESS,
    module: ErrorModule.COMMON,
    message: '成功',
    description: '操作成功'
  },
  [ErrorCode.UNKNOWN_ERROR]: {
    code: ErrorCode.UNKNOWN_ERROR,
    httpStatus: HttpStatus.INTERNAL_SERVER_ERROR,
    category: ErrorCategory.SYSTEM,
    module: ErrorModule.COMMON,
    message: '系统内部错误，请联系管理员',
    description: '未知系统错误'
  },
  // ... 此处省略完整映射，实际使用时建议从后端自动生成
  // 完整映射请参考 scripts/generate-error-codes.ts 生成脚本
};

// ============ 核心工具类 ============

/**
 * API 错误响应结构
 */
export interface ApiErrorResponse {
  code: ErrorCode;
  message: string;
  data?: unknown;
  timestamp: string;
  path: string;
  traceId?: string;
}

/** 格式化错误消息 */
export function formatErrorMessage(template: string, ...args: (string | number)[]): string {
  return template.replace(/\{(\d+)\}/g, (match, index) => {
    const arg = args[parseInt(index, 10)];
    return arg !== undefined ? String(arg) : match;
  });
}

/** 根据错误码获取 HTTP 状态码 */
export function getHttpStatus(code: ErrorCode): HttpStatus {
  // 简化映射，实际使用时建议完整映射表
  if (code >= 30000 && code < 40000) return HttpStatus.FORBIDDEN; // 权限类
  if (code >= 40000 && code < 50000) return HttpStatus.NOT_FOUND; // 资源不存在
  if (code >= 50000 && code < 60000) return HttpStatus.CONFLICT; // 并发冲突
  if (code >= 80000 && code < 90000) return HttpStatus.TOO_MANY_REQUESTS; // 限流
  if (code >= 70000) return HttpStatus.INTERNAL_SERVER_ERROR; // 系统错误
  if (code >= 10000 && code < 20000) return HttpStatus.BAD_REQUEST; // 参数校验
  if (code >= 20000 && code < 30000) return HttpStatus.CONFLICT; // 资源已存在
  if (code >= 60000) return HttpStatus.SERVICE_UNAVAILABLE; // 外部依赖
  return HttpStatus.INTERNAL_SERVER_ERROR;
}

/** 判断是否为重试错误 */
export function isRetryableError(code: ErrorCode): boolean {
  return [
    ErrorCode.SERVICE_UNAVAILABLE,
    ErrorCode.EXTERNAL_SERVICE_TIMEOUT,
    ErrorCode.CIRCUIT_BREAKER_OPEN,
    ErrorCode.RATE_LIMIT_EXCEEDED,
    ErrorCode.CONCURRENT_CONFLICT,
    ErrorCode.VERSION_CONFLICT,
  ].includes(code);
}

/** 判断是否为认证错误 */
export function isAuthError(code: ErrorCode): boolean {
  return [
    ErrorCode.UNAUTHORIZED,
    ErrorCode.TOKEN_EXPIRED,
    ErrorCode.TOKEN_INVALID,
    ErrorCode.REFRESH_TOKEN_EXPIRED,
    ErrorCode.REFRESH_TOKEN_INVALID,
  ].includes(code);
}

/** 判断是否为权限错误 */
export function isPermissionError(code: ErrorCode): boolean {
  return code === ErrorCode.PERMISSION_DENIED;
}

/** 判断是否为资源不存在错误 */
export function isNotFoundError(code: ErrorCode): boolean = code >= 40000 && code < 50000;

/** 格式化错误消息（带参数） */
export function formatError(code: ErrorCode, ...args: (string | number)[]): string {
  // 实际使用时需要完整的消息模板映射表
  // 这里提供基础框架，实际使用时建议从后端同步完整映射
  return String(code);
}

/** API 错误类 */
export class ApiError extends Error {
  public readonly code: ErrorCode;
  public readonly httpStatus: HttpStatus;
  public readonly data?: unknown;
  public readonly timestamp: string;
  public readonly path: string;
  public readonly traceId?: string;

  constructor(
    code: ErrorCode,
    message?: string,
    data?: unknown,
    path?: string,
    traceId?: string
  ) {
    super(message || String(code));
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = getHttpStatus(code);
    this.data = data;
    this.timestamp = new Date().toISOString();
    this.path = path || '';
    this.traceId = traceId;
  }

  /** 判断是否可重试 */
  isRetryable(): boolean {
    return isRetryableError(this.code);
  }

  /** 判断是否为认证错误 */
  isAuthError(): boolean {
    return isAuthError(this.code);
  }

  /** 判断是否为权限错误 */
  isPermissionError(): boolean {
    return isPermissionError(this.code);
  }

  /** 转换为普通对象 (用于上报/日志) */
  toJSON(): ApiErrorResponse {
    return {
      code: this.code,
      message: this.message,
      data: this.data,
      timestamp: this.timestamp,
      path: this.path,
      traceId: this.traceId,
    };
  }
}

/** 创建 API 错误的工厂函数 */
export function createApiError(
  code: ErrorCode,
  message?: string,
  data?: unknown,
  path?: string
): ApiError {
  return new ApiError(code, message, data, path);
}

/** 从后端响应创建 API 错误 */
export function createApiErrorFromResponse(response: {
  code: number;
  message: string;
  data?: unknown;
  timestamp: string;
  path: string;
}): ApiError {
  return new ApiError(
    response.code as ErrorCode,
    response.message,
    response.data,
    response.path
  );
}

// ============ 导出 ============

export default {
  ErrorCode,
  ErrorCategory,
  ErrorModule,
  HttpStatus,
  ErrorCodeMeta,
  ApiError,
  createApiError,
  createApiErrorFromResponse,
  getHttpStatus,
  isRetryableError,
  isAuthError,
  isPermissionError,
  formatErrorMessage,
};
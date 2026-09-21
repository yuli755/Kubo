import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve('SPMS-Server/src/main/java/cn/hamm/spms')

function code(file: string) {
  return readFileSync(resolve(root, file), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

describe('p3 · 后端业务核心能力全量回归', () => {
  describe('1. 登录与防暴力破解（Login / Brute Force / Token Revocation）', () => {
    it('密码散列契约：使用 SHA-1 双向加盐散列，统一 40 位小写十六进制', () => {
      const hasher = code('framework/security/PasswordHasher.java')
      expect(hasher).toContain('MessageDigest.getInstance("SHA-1")')
      expect(hasher).toContain('password + salt')
      expect(hasher).toContain('salt + password')
      expect(hasher).toContain('HEX_CHARS[v >>> 4]')
      expect(hasher).toContain('HEX_CHARS[v & 0x0F]')

      const userService = code('module/personnel/user/UserService.java')
      expect(userService).toContain('PasswordHasher.encode(entity.getPassword(), salt)')
      expect(userService).not.toContain('PasswordUtil')
    })

    it('登录防爆破守卫：账号阈值 5 次 / 15分钟，IP 阈值 50 次，成功清理计数', () => {
      const guard = code('common/security/LoginBruteForceGuard.java')
      expect(guard).toContain('MAX_FAIL_ATTEMPTS = 5')
      expect(guard).toContain('FAIL_WINDOW_SECONDS = 15 * 60')
      expect(guard).toContain('LOCK_SECONDS = 15 * 60')
      expect(guard).toContain('IP_MAX_FAIL_ATTEMPTS = 50')
      expect(guard).toContain('FAIL_KEY_PREFIX = "kubo:login:fail:"')
      expect(guard).toContain('LOCK_KEY_PREFIX = "kubo:login:lock:"')
      expect(guard).toContain('assertNotLocked(@Nullable String account, @Nullable String ip)')
      expect(guard).toContain('recordFailure(@Nullable String account, @Nullable String ip)')
      expect(guard).toContain('clear(@Nullable String account, @Nullable String ip)')
    })

    it('用户控制器登录：先验锁定，成功清理计数，失败统计自持业务异常', () => {
      const userCtrl = code('module/personnel/user/UserController.java')
      expect(userCtrl).toContain('loginBruteForceGuard.assertNotLocked(account, clientIp);')
      expect(userCtrl).toMatch(/catch\s*\(BusinessException exception\)\s*\{\s*loginBruteForceGuard\.recordFailure\(account, clientIp\);\s*throw exception;/)
      expect(userCtrl).toContain('loginBruteForceGuard.clear(account, clientIp);')
    })

    it('退出登录：清理 Redis 映射、重置授权 Cookie、并向令牌黑名单登记吊销', () => {
      const userCtrl = code('module/personnel/user/UserController.java')
      expect(userCtrl).toContain('service.deleteCookie(cookieString);')
      expect(userCtrl).toContain('cookie.setMaxAge(0);')
      expect(userCtrl).toContain('accessTokenRevocationService.revoke(')
    })
  })

  describe('2. 租户隔离与权限拦截（Tenant / Permission / Interceptor）', () => {
    it('全局请求拦截器：实现 HandlerInterceptor，注入 Tracer 追踪标识，完成生命周期清理', () => {
      const interceptor = code('common/interceptor/RequestInterceptor.java')
      expect(interceptor).toContain('implements HandlerInterceptor')
      expect(interceptor).toContain('Tracer.setTraceId(traceId)')
      expect(interceptor).toContain('response.setHeader("X-Trace-ID", traceId)')
      expect(interceptor).toContain('PermissionContextHolder.clear()')
      expect(interceptor).toContain('MaskedInputRegistry.clear()')
      expect(interceptor).toContain('Tracer.clear()')
    })

    it('自持权限扫描器：严格提取控制器标识与 @Permission 访问要求', () => {
      const scanner = code('common/security/PermissionScanner.java')
      expect(scanner).toContain('getControllerIdentity(@NotNull Class<?> clazz)')
      expect(scanner).toContain('getAccessRequirement(@NotNull Class<?> clazz, @NotNull Method method)')
      expect(scanner).toContain('public record AccessRequirement(')
    })

    it('租户上下文解析与隔离：从请求令牌解析租户标识，建立隔离屏障', () => {
      const resolver = code('module/system/datapermission/service/TenantContextResolver.java')
      expect(resolver).toContain('public Long resolve(UserEntity user, HttpServletRequest request)')
      expect(resolver).toContain('TENANT_HEADER = "X-Tenant-Id"')
      expect(resolver).toContain('properties.getDefaultTenantId()')
      const entity = code('base/BaseEntity.java')
      expect(entity).toContain('private Long id;')
      expect(entity).toContain('private Boolean isDisabled = false;')
      expect(entity).toContain('private Long createTime;')
      expect(entity).toContain('private Long updateTime;')
    })
  })

  describe('3. 单据状态机与库存事务（Bill State Machine / Inventory Transactions）', () => {
    it('单据状态机闸门：只允许在已审核（AUDITED）状态下录入完成数量', () => {
      const billService = code('base/bill/AbstractBaseBillService.java')
      expect(billService).toContain('public final boolean canAddFinish(@NotNull E bill)')
      expect(billService).toContain('getAuditedStatus().equalsKey(bill.getStatus())')
      expect(billService).toContain('单据当前状态不允许录入完成数量（需先审核通过）')
    })

    it('单据完成生命周期：明细完成状态检查、前置钩子与终态持久化', () => {
      const billService = code('base/bill/AbstractBaseBillService.java')
      expect(billService).toContain('public final void setBillDetailsAllFinished(long billId)')
      expect(billService).toContain('public final void setBillFinished(long billId)')
      expect(billService).toContain('beforeBillFinish(billId)')
      expect(billService).toContain('afterBillFinished(billId)')
    })

    it('仓储入库/出库/移库服务：继承自持单据基类，通过事务帮助类执行库存增减', () => {
      const inputService = code('module/wms/input/InputService.java')
      const outputService = code('module/wms/output/OutputService.java')
      const moveService = code('module/wms/move/MoveService.java')
      expect(inputService).toContain('extends AbstractBaseBillService')
      expect(outputService).toContain('extends AbstractBaseBillService')
      expect(moveService).toContain('extends AbstractBaseBillService')
    })
  })

  describe('4. 数据导出列脱敏与防旁路（Export Masking / Formula Protection）', () => {
    it('导出列级遮蔽：先复制实体副本再脱敏，避免持久化上下文脏写回库', () => {
      const masker = code('module/system/datapermission/service/ExportColumnMasker.java')
      expect(masker).toContain('public static <E> List<E> maskForExport(List<E> entities)')
      expect(masker).toContain('BeanUtils.copyProperties(entity, instance)')
      expect(masker).toContain('maskingService.maskFieldValue(')
      expect(masker).toContain('REASON_EXPORT_WITHOUT_CONTEXT = "export-without-context"')
    })

    it('导出防旁路守卫：未取得 EXPORT 明文授权的受限字段在强制模式下直接拦截', () => {
      const guard = code('module/system/datapermission/service/ExportBypassGuard.java')
      expect(guard).toContain('public void guard(HttpServletRequest request)')
      expect(guard).toContain('ACTION_EXPORT = "EXPORT"')
      expect(guard).toContain('REASON_EXPORT_BYPASS = "export-bypass"')
      expect(guard).toContain('DataPermissionDenial.denied(')
    })

    it('cSV 公式注入防护：转义 =, +, -, @, \\t, \\r 前导字符，防止 Excel 命令执行', () => {
      const sanitizer = code('common/CsvFormulaSanitizer.java')
      expect(sanitizer).toContain('CsvText.protectFormula(value)')
      const csvText = code('framework/util/CsvText.java')
      expect(csvText).toContain('value == \'=\' || value == \'+\' || value == \'-\' || value == \'@\'')
      expect(csvText).toContain('value == \'\\t\' || value == \'\\r\' || value == \'\\n\'')
      expect(csvText).toContain('return "\'" + value;')
    })
  })
})

package cn.hamm.spms.module.asset.device;

import cn.hamm.airpower.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>设备采集端 API-Key 契约测试（A1）</h1>
 *
 * <p>锁住三条底线：常量时间比对只认「非空且逐字节相等」；设备不存在与密钥不匹配返回同一 401；
 * 密钥只在新建时自动生成、更新不重置（避免保存一次就踢掉采集端）。</p>
 */
@DisplayName("DeviceService · 采集端 API-Key 认证")
class DeviceApiKeyTest {

    private final DeviceService service = new DeviceService();

    @Test
    @DisplayName("apiKeyMatches：逐字节相等才匹配")
    void matchesOnlyExact() {
        assertTrue(DeviceService.apiKeyMatches("k-abc123", "k-abc123"));
        assertFalse(DeviceService.apiKeyMatches("k-abc123", "k-abc124"));
        assertFalse(DeviceService.apiKeyMatches("k-abc123", "K-ABC123")); // 大小写敏感
        assertFalse(DeviceService.apiKeyMatches("k-abc123", "k-abc12"));   // 长度不同
    }

    @Test
    @DisplayName("apiKeyMatches：任一为空（null/空串）都不匹配，杜绝空密钥绕过")
    void emptyOrNullNeverMatches() {
        assertFalse(DeviceService.apiKeyMatches(null, "k-abc123"));
        assertFalse(DeviceService.apiKeyMatches("k-abc123", null));
        assertFalse(DeviceService.apiKeyMatches(null, null));
        assertFalse(DeviceService.apiKeyMatches("", ""));
        assertFalse(DeviceService.apiKeyMatches("", "k-abc123"));
        assertFalse(DeviceService.apiKeyMatches("k-abc123", ""));
    }

    @Test
    @DisplayName("authenticateDevice：设备不存在与密钥不匹配都抛 401（ServiceException）")
    void authenticateRejectsMissingDeviceAndMismatch() {
        // 设备不存在
        assertThrows(ServiceException.class, () -> service.authenticateDevice("k-abc123", null));

        DeviceEntity device = new DeviceEntity().setApiKey("k-expected");
        // 密钥不匹配
        assertThrows(ServiceException.class, () -> service.authenticateDevice("k-wrong", device));
        // 密钥为空
        assertThrows(ServiceException.class, () -> service.authenticateDevice(null, device));
        // 密钥匹配 → 不抛
        service.authenticateDevice("k-expected", device);
    }

    @Test
    @DisplayName("beforeAppSaveToDatabase：新建自动生成非空密钥")
    void generatesApiKeyOnCreate() {
        DeviceEntity device = new DeviceEntity();
        device.setCode("D-001");
        service.beforeAppSaveToDatabase(device);
        assertNotNull(device.getApiKey());
        assertFalse(device.getApiKey().isEmpty());
        assertEquals("D-001", device.getUuid(), "uuid 缺省回填 code 的既有行为不应被破坏");
    }

    @Test
    @DisplayName("beforeAppSaveToDatabase：更新不重置已有密钥")
    void doesNotRegenerateOnUpdate() {
        DeviceEntity device = new DeviceEntity();
        device.setId(42L);
        device.setCode("D-001");
        device.setUuid("uuid-1");
        device.setApiKey("k-existing");
        service.beforeAppSaveToDatabase(device);
        assertEquals("k-existing", device.getApiKey(), "更新路径不得重置密钥，否则保存一次采集端就掉线");
    }

    @Test
    @DisplayName("beforeAppSaveToDatabase：新建时显式指定的密钥被保留（幂等/自定义）")
    void keepsExplicitKeyOnCreate() {
        DeviceEntity device = new DeviceEntity();
        device.setCode("D-001");
        device.setApiKey("k-custom");
        service.beforeAppSaveToDatabase(device);
        assertEquals("k-custom", device.getApiKey());
    }

    @Test
    @DisplayName("generateApiKey：生成非空且带 Base64 随机字节（与 AppSecret 同口径）")
    void generatedKeyIsNonEmptyBase64() {
        String first = service.generateApiKey();
        String second = service.generateApiKey();
        assertNotNull(first);
        assertTrue(first.length() >= 16, "随机密钥长度应足够，实际 " + first.length());
        assertFalse(first.equals(second), "两次生成的密钥不应相同");
    }
}

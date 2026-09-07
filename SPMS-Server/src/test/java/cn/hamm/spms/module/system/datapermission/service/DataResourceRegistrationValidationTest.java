package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <h1>资源登记的保存期拦截</h1>
 *
 * <p>{@code data_resource} 是自由文本表，而它写错的后果在运行期毫无痕迹（条件绑不上 ⇒
 * enforce 查空、shadow 静默）。本类只负责"把明显写错的挡在门外"，判据仍来自
 * {@code OwnerColumnRegistration}：这里不断言消息文本（那是语法那一份用例的职责），
 * 只断言"合法的要过、非法的要挡"。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataResourceService · 登记串保存期校验")
class DataResourceRegistrationValidationTest {

    private static DataResourceEntity resource(String tenantColumn, String ownerColumns, String tableName) {
        return new DataResourceEntity()
                .setTenantColumn(tenantColumn)
                .setOwnerColumns(ownerColumns)
                .setTableName(tableName)
                .setResourceKey("wms.input");
    }

    private static void assertRejected(Executable call) {
        assertThrows(RuntimeException.class, call, "登记串非法时必须挡下来，不能静默存进去");
    }

    @Test
    @DisplayName("裸写法、带维度前缀、关联路径都能存")
    void validRegistrationsAreAccepted() {
        assertDoesNotThrow(() -> DataResourceService.validateRegistrations(resource("tenant_id", null, "input")));
        assertDoesNotThrow(() -> DataResourceService.validateRegistrations(
                resource("tenantId", "creatorId,DEPARTMENT:deptId", "input")));
        assertDoesNotThrow(() -> DataResourceService.validateRegistrations(
                resource(null, "WAREHOUSE:storage.id", "input")));
    }

    @Test
    @DisplayName("空 null 入参不炸（框架在某些路径会传空壳实体）")
    void nullEntityIsIgnored() {
        assertDoesNotThrow(() -> DataResourceService.validateRegistrations(null));
    }

    @Test
    @DisplayName("维度前缀写错 / 超过一跳 → 保存被拒")
    void malformedOwnerColumnsAreRejected() {
        assertRejected(() -> DataResourceService.validateRegistrations(
                resource("tenant_id", "WAREHOUSEX:storage.id", "input")));
        assertRejected(() -> DataResourceService.validateRegistrations(
                resource("tenant_id", "storage.owner.id", "input")));
        assertRejected(() -> DataResourceService.validateRegistrations(
                resource("tenant_id", "a; DROP TABLE x", "input")));
    }

    @Test
    @DisplayName("租户列写成怪形状 → 保存被拒（它同样要能对上实体属性）")
    void oddTenantColumnIsRejected() {
        assertRejected(() -> DataResourceService.validateRegistrations(
                resource("tenant id", "creatorId", "input")));
        assertRejected(() -> DataResourceService.validateRegistrations(
                resource("tenant_id_", "creatorId", "input")));
    }

    @Test
    @DisplayName("表名为空 → 保存被拒：它丢了就等于所有维度都判成「实体未映射」")
    void blankTableNameIsRejected() {
        assertRejected(() -> DataResourceService.validateRegistrations(resource("tenant_id", "creatorId", "  ")));
        assertRejected(() -> DataResourceService.validateRegistrations(resource("tenant_id", "creatorId", null)));
    }
}

package cn.hamm.spms.common.exception;

import cn.hamm.airpower.core.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 F-2：约束错误语义映射契约测试。
 *
 * <p>锁死「4034 不再一锅端」：唯一键冲突/外键引用/超长/非空各自给出正确语义，
 * 框架旧口径（全部报「删除失败，数据正在使用中」）必须被本 advice 覆盖。</p>
 */
@DisplayName("DataIntegrityViolationAdvice · 约束错误精确映射")
class DataIntegrityViolationAdviceTest {

    private final DataIntegrityViolationAdvice advice = new DataIntegrityViolationAdvice();

    private static DataIntegrityViolationException wrapped(String driverMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(driverMessage));
    }

    @Test
    @DisplayName("唯一键冲突 → 数据已存在（不再误报「正在使用中」）")
    void duplicateEntryMapsToExist() {
        Json json = advice.handle(wrapped("Duplicate entry 'a@b.com' for key 'user.idx_email'"));
        assertTrue(json.getMessage().contains("数据已存在"), json.getMessage());
        assertTrue(json.getMessage().contains("a@b.com"), json.getMessage());
    }

    @Test
    @DisplayName("删除被引用行 → 保留「正在使用中」语义")
    void deleteParentReferencedKeepsUsedSemantics() {
        Json json = advice.handle(wrapped(
                "Cannot delete or update a parent row: a foreign key constraint fails (`kubo`.`inventory`, CONSTRAINT `fk` FOREIGN KEY (`material_id`) REFERENCES `material` (`id`))"));
        assertTrue(json.getMessage().contains("正被其他数据引用"), json.getMessage());
    }

    @Test
    @DisplayName("子行引用缺失父行 → 关联数据不存在")
    void childRowViolationMapsToForbidden() {
        Json json = advice.handle(wrapped(
                "Cannot add or update a child row: a foreign key constraint fails"));
        assertTrue(json.getMessage().contains("关联数据不存在"), json.getMessage());
    }

    @Test
    @DisplayName("字段超长 / 非空缺失 → 参数级错误并点名列")
    void lengthAndNotNullMapToParamInvalid() {
        Json longJson = advice.handle(wrapped("Data too long for column 'name' at row 1"));
        assertTrue(longJson.getMessage().contains("name"), longJson.getMessage());
        assertTrue(longJson.getMessage().contains("长度"), longJson.getMessage());

        Json nullJson = advice.handle(wrapped("Column 'phone' cannot be null"));
        assertTrue(nullJson.getMessage().contains("phone"), nullJson.getMessage());
    }

    @Test
    @DisplayName("未识别约束消息 → 通用冲突文案（绝不回退「正在使用中」）")
    void unknownConstraintGetsGenericMessage() {
        Json json = advice.handle(wrapped("Check constraint 'chk_x' is violated."));
        assertTrue(json.getMessage().contains("约束"), json.getMessage());
        assertTrue(!json.getMessage().contains("正在使用中"), json.getMessage());
    }

    @Test
    @DisplayName("Spring 类型化子类（DuplicateKeyException）同样被截获")
    void typedSubclassHandled() {
        Json json = advice.handle(new DuplicateKeyException(
                "Statement; SQL [n/a]; Duplicate entry '2-3' for key 'inventory.uk_material_storage'"));
        assertTrue(json.getMessage().contains("数据已存在"), json.getMessage());
    }

    @Test
    @DisplayName("错误码与框架体系一致（FORBIDDEN_EXIST=4031）")
    void errorCodesAreBusinessErrorCodes() {
        Json json = advice.handle(wrapped("Duplicate entry 'x' for key 'k'"));
        assertEquals(4031, json.getCode());
    }
}

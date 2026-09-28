package cn.hamm.spms.common.export;

import cn.hamm.spms.common.CsvFormulaSanitizer;
import cn.hamm.spms.module.asset.contract.ContractEntity;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.asset.material.enums.MaterialType;
import cn.hamm.spms.module.asset.material.enums.MaterialUseType;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.channel.saleprice.SalePriceEntity;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.output.OutputEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>十四个真实业务实体的导出列与格式接线契约</h1>
 *
 * <p>需要完整 Maven 依赖；独立 Java 工具测试不会运行本类。</p>
 *
 * @author Hamm.cn
 */
class EntityCsvExportTest {
    @TestFactory
    Stream<DynamicTest> allMapperExportsKeepTheirHeaderContract() {
        return Stream.of(
                new HeaderCase(MaterialEntity.class, "物料名称,物料编码,规格型号,物料类型,使用方式,采购标准价,销售标准价"),
                new HeaderCase(ContractEntity.class, "合同编码,合同名称,合同类型,合同状态"),
                new HeaderCase(CustomerEntity.class, "客户名称,客户编码,联系电话"),
                new HeaderCase(SupplierEntity.class, "供应商名称,供应商编码,联系电话"),
                // 这两个实体确实带 @Export 列（Purchase: billCode/reason/totalPrice/totalRealPrice/status，
                // Sale: billCode/reason/totalPrice/status），表头由 EntityMetadata.exportColumns 按
                // @Export + @Description 生成。原期望值 "" 是过期契约；本类此前因 dev 后端编译不过
                // 而从未真正运行，故一直没暴露。
                new HeaderCase(PurchaseEntity.class, "采购单号,采购事由,总金额,实际金额,采购状态"),
                new HeaderCase(SaleEntity.class, "销售单号,销售说明,总金额,销售状态"),
                new HeaderCase(PurchasePriceEntity.class, "采购单价"),
                new HeaderCase(SalePriceEntity.class, "销售单价"),
                new HeaderCase(StorageEntity.class, "仓库编码,名称"),
                new HeaderCase(StructureEntity.class, "生产单元编码,名称,生产单元类型"),
                new HeaderCase(InputEntity.class, "入库单号,入库状态,入库类型"),
                new HeaderCase(OutputEntity.class, "出库单号,出库状态,出库类型"),
                new HeaderCase(MoveEntity.class, "移库单号,移库状态"),
                new HeaderCase(InventoryEntity.class, "库存数量,存储类型")
        ).map(sample -> DynamicTest.dynamicTest(sample.type().getSimpleName(),
                () -> assertEquals(sample.header(), header(sample.type()))));
    }

    @Test
    void materialDictionaryNumbersAndMissingTextKeepExistingShape() throws Exception {
        MaterialType type = MaterialType.values()[0];
        MaterialUseType use = MaterialUseType.values()[0];
        MaterialEntity entity = new MaterialEntity().setName("物料").setCode("001")
                .setMaterialType(type.getKey()).setUseType(use.getKey()).setPurchasePrice(1.25).setSalePrice(2.5);
        String csv = read(EntityCsvExport.toCsvInputStream(List.of(entity), MaterialEntity.class));
        assertEquals("物料名称,物料编码,规格型号,物料类型,使用方式,采购标准价,销售标准价\n"
                + "\t物料,\t001,\t-," + type.getLabel() + ',' + use.getLabel() + ",1.25,2.5", csv);
    }

    @Test
    void previouslySanitizedTextIsNotDoubleEscapedOrWrittenBack() throws Exception {
        CustomerEntity entity = new CustomerEntity().setName("=cmd").setCode("001").setPhone("1******0");
        String csv = read(EntityCsvExport.toCsvInputStream(CsvFormulaSanitizer.sanitize(List.of(entity)), CustomerEntity.class));
        assertEquals("客户名称,客户编码,联系电话\n\t'=cmd,\t001,\t1******0", csv);
        assertEquals("=cmd", entity.getName());
    }

    @Test
    void undeclaredFieldsDoNotAppearInExport() throws Exception {
        ContractEntity entity = new ContractEntity().setCode("C1").setName("合同").setContent("NEVER_EXPORT_THIS_CONTENT");
        String csv = read(EntityCsvExport.toCsvInputStream(List.of(entity), ContractEntity.class));
        assertFalse(csv.contains("NEVER_EXPORT_THIS_CONTENT"));
    }

    @Test
    void retiredPickingColumnIsReadOnlyAndNotExposed() throws Exception {
        var field = OutputEntity.class.getDeclaredField("legacyPickingId");
        Column column = field.getAnnotation(Column.class);
        assertEquals("picking_id", column.name());
        assertFalse(column.insertable());
        assertFalse(column.updatable());
        assertTrue(field.isAnnotationPresent(JsonIgnore.class));
    }

    private static <T> String header(Class<T> type) throws Exception {
        return read(EntityCsvExport.toCsvInputStream(List.of(), type));
    }

    private static String read(InputStream input) throws Exception {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record HeaderCase(Class<?> type, String header) {
    }
}

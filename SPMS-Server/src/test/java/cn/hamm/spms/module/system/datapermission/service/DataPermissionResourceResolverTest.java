package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceService;
import cn.hamm.spms.module.channel.saleprice.SalePriceService;
import cn.hamm.spms.module.personnel.user.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>资源键解析必须与 data_resource 的注册口径一致</h1>
 *
 * <p>这条测试针对的是一个"配置看起来成功、防护实际为零"的缺陷：解析器曾经把实体名整串小写，
 * 于是 {@code PurchasePriceService} 解析成 {@code channel.purchaseprice}，
 * 而注册键是 {@code channel.purchasePrice} —— 查不到资源就等于没有策略，
 * 但接口不会报错、单测也不会红（没人给这个模块配过策略时更是完全无感）。</p>
 *
 * <p>所以断言的锚点是<b>注册表里的真实键名</b>，不是"我以为的命名规则"。</p>
 *
 * @author Hamm.cn
 */
class DataPermissionResourceResolverTest {

    @Test
    void camelCaseResourcesResolveToTheRegisteredKey() {
        assertEquals("channel.purchasePrice", DataPermissionResourceResolver.resolve(PurchasePriceService.class),
                "注册键是 camelCase，解析结果必须一字不差（大小写敏感）");
        assertEquals("channel.salePrice", DataPermissionResourceResolver.resolve(SalePriceService.class));
    }

    @Test
    void singleWordEntitiesKeepWorking() {
        assertEquals("asset.material", DataPermissionResourceResolver.resolve(MaterialService.class));
        assertEquals("personnel.user", DataPermissionResourceResolver.resolve(UserService.class));
    }

    @Test
    void nonServiceClassYieldsNoResource() {
        assertNull(DataPermissionResourceResolver.resolve(String.class), "不是 *Service 就不该硬凑一个键");
        assertNull(DataPermissionResourceResolver.resolve(null));
    }

    /**
     * 缩写开头的类名不能被降首字母（{@code BOMService} 不该变成 {@code bOMService}）
     */
    @Test
    void acronymsAreLeftAlone() {
        assertEquals("BOM", DataPermissionResourceResolver.decapitalize("BOM"), "前两位都大写视为缩写，原样保留");
        assertEquals("URL", DataPermissionResourceResolver.decapitalize("URL"));
        assertEquals("bom", DataPermissionResourceResolver.decapitalize("Bom"), "只有首字母大写时才降格");
        assertEquals("order", DataPermissionResourceResolver.decapitalize("Order"));
        assertEquals("", DataPermissionResourceResolver.decapitalize(""));
    }
}

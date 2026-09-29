package cn.hamm.spms;

import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.config.enums.ConfigType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>演示数据初始化闸门（K 报告 §4 P2-10）</h1>
 *
 * <p>闸门的判据曾是运行目录下的 {@code init.lock} 文件 —— 相对路径意味着「在哪启动」会改变
 * 幂等性：从 {@code SPMS-Server/} 启动与从仓库根用 {@code mvn -f SPMS-Server/pom.xml spring-boot:run}
 * 启动，锁文件落在两个目录、互不相认，同一份库可能被重复初始化（演示数据翻倍），也可能被别处
 * 留下的锁文件挡住。改造后标志位落在数据源（{@code config} 表的
 * {@link ConfigFlag#DEV_DATA_INIT_DONE}），与运行目录彻底解耦。</p>
 *
 * <p>判据抽成 {@link DevDataInitRunner#shouldInitialize} 这个纯函数，因此能脱离 Spring 容器把整张
 * 真值表测完 —— 这类「初始化只跑一次」的闸门一旦回归，症状是演示数据翻倍或库空着，两种都要重启
 * 才能复现，不适合靠人肉发现。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DevDataInitRunner · 初始化闸门判据")
class DevDataInitGateTest {

    @Test
    @DisplayName("全新库：没有任何证据 → 初始化")
    void initializesOnFreshDatabase() {
        assertTrue(DevDataInitRunner.shouldInitialize(null, false, false, false),
                "全新环境必须初始化，否则没有任何演示数据与账号");
    }

    @Test
    @DisplayName("数据源标志位已置位 → 跳过（与运行目录、锁文件、历史数据都无关）")
    void skipsWhenDataSourceFlagIsSet() {
        assertFalse(DevDataInitRunner.shouldInitialize(true, false, false, false),
                "标志位已置位却重复初始化，会让演示数据翻倍");
        assertFalse(DevDataInitRunner.shouldInitialize(true, true, true, false),
                "标志位优先于遗留证据：换了目录、库里已有数据，都不该再播种");
    }

    @Test
    @DisplayName("管理员显式置 0 → 重新初始化，遗留证据不得否决这个意图")
    void explicitZeroOverridesLegacyEvidence() {
        // 这条是「把标志位改回 0 就能重新播种」的文档承诺：若遗留证据（锁文件 / 种子用户）
        // 能否决显式的 0，LOGIN.md 里那条操作指引就成了假话。
        assertTrue(DevDataInitRunner.shouldInitialize(false, false, false, false), "显式 0 → 初始化");
        assertTrue(DevDataInitRunner.shouldInitialize(false, true, false, false),
                "显式 0 时，遗留 init.lock 不得否决");
        assertTrue(DevDataInitRunner.shouldInitialize(false, false, true, false),
                "显式 0 时，库里已有种子用户也不得否决（否则永远无法重新播种）");
        assertTrue(DevDataInitRunner.shouldInitialize(false, true, true, false), "显式 0 覆盖全部遗留证据");
    }

    @Test
    @DisplayName("过渡期（标志位行还不存在）：锁文件或种子用户任一命中即视为已初始化")
    void legacyEvidenceBridgesOldInstalls() {
        assertFalse(DevDataInitRunner.shouldInitialize(null, true, false, false),
                "老环境只有 init.lock 时必须跳过，否则升级后被重新播种一遍");
        assertFalse(DevDataInitRunner.shouldInitialize(null, false, true, false),
                "从别的目录启动看不到 init.lock，但库里有种子用户 —— 仍必须跳过（这正是 CWD 漏洞）");
        assertFalse(DevDataInitRunner.shouldInitialize(null, true, true, false), "两种证据都在，跳过");
    }

    @Test
    @DisplayName("create-drop 是显式重建，优先级最高")
    void createDropAlwaysRebuilds() {
        assertTrue(DevDataInitRunner.shouldInitialize(null, false, false, true), "全新库 + create-drop");
        assertTrue(DevDataInitRunner.shouldInitialize(true, false, false, true),
                "create-drop 会重建表、标志位随之消失，必须重新初始化");
        assertTrue(DevDataInitRunner.shouldInitialize(false, true, true, true), "create-drop 覆盖一切");
    }

    @Test
    @DisplayName("标志位配置项存在、是布尔型、默认关闭（默认值必须是「未初始化」）")
    void initDoneFlagIsBooleanAndDefaultsToOff() {
        ConfigFlag flag = ConfigFlag.DEV_DATA_INIT_DONE;
        assertEquals(ConfigType.BOOLEAN, flag.getType(), "标志位必须是布尔型，否则 booleanConfig() 判不出来");
        assertEquals("0", flag.getDefaultValue(),
                "默认值必须是 0（未初始化）：默认置 1 等于全新库永远不初始化");
        assertEquals("演示数据已初始化", flag.getLabel());
    }

    @Test
    @DisplayName("源码契约：闸门走纯函数 + 数据源标志位，且不再写 init.lock 文件")
    void sourceContractUsesDataSourceFlagAndNeverWritesLockFile() throws IOException {
        Path source = Path.of("src/main/java/cn/hamm/spms/DevDataInitRunner.java");
        assertTrue(Files.exists(source), "源码路径变了？请同步本用例的相对路径");
        String text = Files.readString(source, StandardCharsets.UTF_8);

        assertTrue(text.contains("shouldInitialize(flagInDataSource, legacyLockFileExists, isLegacySeedPresent(), rebuildSchema)"),
                "闸门必须走 shouldInitialize 这个可单测的纯函数，不要把判据散回 run() 里");
        assertTrue(text.contains("readDevDataInitFlag()"), "必须读数据源标志位（三态：null 表示行不存在）");
        assertTrue(text.contains("isLegacySeedPresent()"), "过渡期必须有数据推导的遗留证据，不能只信锁文件");
        assertTrue(text.contains("markDevDataInitDone();"), "初始化完成后必须把标志位写回数据源");

        // 写文件的那段必须彻底消失：留着就会重新引入「运行目录决定幂等性」的老问题
        assertFalse(text.contains("createNewFile()"),
                "不应再创建 init.lock 文件（标志位已在数据源，写文件会让 CWD 重新变得有意义）");
        assertTrue(text.contains("new File(LOCK_FILE).exists()"),
                "遗留锁文件仍需被读一次用于过渡（命中即迁入数据源），不要连兼容一起删掉");
    }
}

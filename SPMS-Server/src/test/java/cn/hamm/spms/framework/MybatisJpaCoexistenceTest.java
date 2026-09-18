package cn.hamm.spms.framework;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>MyBatis-Plus 与残留 JPA 并存的最小验证（P2 换轨的前提）</h1>
 *
 * <p>本类<em>不测任何业务读路径</em>。§19.34 之后仓里多了一个读侧 Mapper（{@code MaterialMapper}）与一份 XML，
 * 但默认配置下它们<b>根本不会注册</b>（开关 {@code kubo.data-permission.mapper-read.enabled=false}），
 * 所以本类的口径不变：它只回答一个此前只能靠猜的问题 —— <b>把 MyBatis-Plus 放进这个应用，
 * 两件事会不会互相挤掉</b>；外加一条：那个开关是否真的能把注册一刀切断（见最后一例）。
 * 风险表里那行"MyBatis 与 AirPower-JPA 并存 → 事务/一二级缓存语义不一致"（🔴 P0）说的就是这个，
 * 而它无法在本地验证（沙箱没有 JDK、也没有依赖树），所以把它做成一次可回归的断言，
 * 而不是"我看着应该没问题"。</p>
 *
 * <p><b>J3 之后这里的"JPA"指什么</b>：实体上的 JPA 注解已经退场（41 个实体只剩 {@code @TableName}），
 * 所以 JPA 侧的实体集合是空的；本类验的不再是"两套 ORM 抢同一张表"，而是
 * <b>"残留 JPA 还在原位、且没有第二个 EntityManagerFactory / 第二个事务管理器"</b>。
 * J4 删掉 {@code spring-boot-starter-data-jpa} 时，本类里那两条断言会红 —— 那是预期信号
 * （届时事务管理器应当变成 {@code DataSourceTransactionManager}）。</p>
 *
 * <p>档位刻意钉在 {@code kubo.data-permission.mode=off}：本类要验的是两套 ORM 能不能共存，
 * 掺进权限判定只会让失败原因分不清是谁（同 §19.29 的教训：<em>集成测试要显式声明它站在哪一档</em>，
 * 哪怕这一档是"都不介入"）。</p>
 *
 * @author Hamm.cn
 */
@SpringBootTest(
        properties = {
                "spring.cache.type=none",
                "kubo.data-permission.mode=off"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
@DisplayName("MyBatis-Plus 与 JPA 并存（P2 前提）")
class MybatisJpaCoexistenceTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    private SqlSessionFactory sqlSessionFactory() {
        SqlSessionFactory factory = applicationContext.getBeanProvider(SqlSessionFactory.class).getIfAvailable();
        assertNotNull(factory, "MyBatis-Plus 的自动配置没起来：starter 没生效或被 airpower 的条件挤掉了，换轨前提不成立");
        return factory;
    }

    @Test
    @DisplayName("加进来之后，JPA 那套还在原位（不能被自动配置悄悄换掉）")
    void jpaSideStillFullyWired() {
        assertNotNull(applicationContext.getBeanProvider(EntityManagerFactory.class).getIfAvailable(),
                "EntityManagerFactory 没了：影子比对（ShadowDiffService / JpaRowVisibilityProbe）会一起塌。"
                        + "J2 之后 CURD 引擎已不依赖 JPA，但影子线仍是 JPA；到 J4 删 starter 时这条会红，那正是预期信号");
        assertNotNull(entityManager);
        assertEquals(1L, applicationContext.getBeanNamesForType(EntityManagerFactory.class).length,
                "出现第二个 EntityManagerFactory = 两套一级/二级缓存，同一个实体会被两份会话各自持有");
    }

    @Test
    @DisplayName("MyBatis 用的就是容器里那个 DataSource，不是自己新建的连接池")
    void mybatisReusesTheContainerDataSource() {
        assertSame(dataSource, sqlSessionFactory().getConfiguration().getEnvironment().getDataSource(),
                "两套连接池就是两套事务语义：@Transactional 对 Mapper 写入不生效，"
                        + "读写分开提交会造出「JPA 回滚了、Mapper 已落库」这类查不出来的脏数据");
    }

    @Test
    @DisplayName("全容器只有一个事务管理器，且是 JpaTransactionManager")
    void singleTransactionManagerIsJpa() {
        String[] names = applicationContext.getBeanNamesForType(PlatformTransactionManager.class);
        assertEquals(1, names.length,
                "出现了多个事务管理器（含 DataSourceTransactionManager）：@Transactional 会随机绑到其中一个，"
                        + "换轨期间必须先把这个歧义消掉");
        assertInstanceOf(JpaTransactionManager.class, applicationContext.getBean(PlatformTransactionManager.class),
                "事务管理器不是 JPA 那个：残留 JPA（影子线）的 flush/一级缓存语义将不受 @Transactional 管；"
                        + "J4 删掉 starter 之后这里应当变成 DataSourceTransactionManager —— 那时改这条断言，别删这个测试");
    }

    /**
     * 本轮只验「并存」，不验「能改 SQL」。
     *
     * <p><b>这段注释曾经记错了一次，记录如下，别把它当装饰。</b>最初这里写的是「{@code jsqlparser}
     * 本体没有随 {@code mybatis-plus-jsqlparser:3.5.9} 传递进来」，依据是 CI 报的
     * {@code ClassNotFoundException: com.github.jsqlparser.parser.CCJSqlParserUtil}。那是误判：
     * 修好「依赖树被 {@code -q} 压成空串」之后，同一棵树上明明打着
     * {@code com.github.jsqlparser:jsqlparser:jar:5.0} —— 本体一直在，红是因为 <b>5.0 里那个类
     * 不叫 4.x 时代的那个名字</b>。两条教训：</p>
     * <ul>
     *   <li>{@code ClassNotFoundException} 只证明「我以为的那个类名不在」，不证明「那个 jar 不在」；
     *       判「在不在」只能靠依赖树，而依赖树还得先证明它不是空的。</li>
     *   <li>不对外部库的内部类名写断言。本类唯一红过的那条正是这种断言，红在它自己的假设计上，
     *       与被测事实无关。解析面就绪与否，留给下一格注册第一个 {@code InnerInterceptor} 时
     *       由编译与启动自然暴露。</li>
     * </ul>
     * <p>下面钉两件我们自己的不变量：分页插件必须在（不在的话 {@code selectPage} 会静默不分页、
     * {@code total} 恒 0），以及插件是挂在<b>容器里那个</b> {@code MybatisPlusInterceptor} 上、
     * 而不再是每处自己 new 一个。jsqlparser 的实际版本每次 CI 都会以 annotation 打出来，不需要人记。</p>
     */
    @Test
    @DisplayName("MyBatis-Plus 的插件已按 J2 的口径装配：分页永远在，数据权限跟读侧开关")
    void mybatisPlusPluginsAreWired() throws Exception {
        MybatisPlusInterceptor interceptor = applicationContext.getBean(MybatisPlusInterceptor.class);
        assertNotNull(interceptor, "MybatisPlusInterceptor 不在容器里：分页与数据权限插件都挂在它上面");
        assertTrue(interceptor.getInterceptors().stream()
                        .anyMatch(inner -> inner instanceof PaginationInnerInterceptor),
                "分页插件没注册：selectPage 会静默不分页（全量返回）且 total 恒 0");
        assertNotNull(Class.forName("com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder"),
                "mybatis-plus-core 不在 classpath：starter 被依赖管理挤掉了");
    }

    /**
     * 默认关下"没接线"必须是可证的，而不是一句注释。
     *
     * <p>这里防的不是有人把开关打开 —— 那是灰度动作。防的是<b>开关被绕过</b>：读侧接口只要标上
     * {@code @Mapper}，MyBatis-Plus 的自动扫描器就会绕过 {@code @ConditionalOnProperty} 把它注册掉，
     * 于是 {@code application.yml} 里那行 {@code enabled: false} 变成装饰，而"关掉就等于换轨前"
     * 这句话从此不可信。这条断言只在配置为默认时才有意义，所以它必须留在这个不打开开关的上下文里。</p>
     */
    @Test
    @DisplayName("读侧 Mapper 默认不注册，写侧仓储无条件注册：灰度与写路径互不牵连")
    void readMapperOffByDefaultWhileWriteSideIsAlwaysRegistered() {
        String[] mappers = applicationContext.getBeanNamesForType(
                cn.hamm.spms.module.asset.material.mapper.MaterialMapper.class);
        assertEquals(0, mappers.length,
                "默认关却注册上了：多半是有人给 Mapper 接口加了 @Mapper，绕过了 mapper-read 开关（见 KuboBaseMapper 纪律一）");
        assertEquals(0, applicationContext.getBeanNamesForType(
                        cn.hamm.spms.module.system.datapermission.config.MapperReadConfiguration.class).length,
                "注册配置本身被加载了：@ConditionalOnProperty 的键名或 havingValue 漂了");
        // J2：写侧 @MapperScan(markerInterface = ICurdRepository.class) 不挂任何开关 ——
        // 关掉读侧灰度不能把增删改一起关掉，那不是灰度而是停机。这条断言就是钉住这一点。
        assertEquals(1, applicationContext.getBeanNamesForType(
                        cn.hamm.spms.module.asset.contract.ContractRepository.class).length,
                "写侧仓储没注册：增删改会整体失效（@MapperScan 的 marker/basePackages 漂了？）");
    }

    @Test
    @DisplayName("MyBatis 能查到 JPA 那张表（同一库、同一 schema）")
    void mybatisReadsTheSameSchema() throws Exception {
        String countSql = SqlFixtures.text("coexistence/count_data_resource.sql");
        // SQL-IGNORE: createNativeQuery 是 JPA 的原生查询入口（语句本身在 coexistence/count_data_resource.sql）
        long viaJpa = ((Number) entityManager.createNativeQuery(countSql)
                .getSingleResult()).longValue();
        long viaMyBatis;
        try (SqlSession session = sqlSessionFactory().openSession()) {
            try (Statement statement = session.getConnection().createStatement();
                 ResultSet rs = statement.executeQuery(countSql)) {
                assertTrue(rs.next(), "COUNT 查询必须返回一行");
                viaMyBatis = rs.getLong(1);
            }
        }
        assertEquals(viaJpa, viaMyBatis,
                "两套 ORM 读到的行数不同：测试库里存在两份 schema 或两条连接指向不同库，换轨的对账前提不成立");
    }
}

package cn.hamm.spms.framework;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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

/**
 * <h1>MyBatis-Plus 与 airpower-JPA 并存的最小验证（P2 换轨的前提）</h1>
 *
 * <p>本类<em>不测任何业务读路径</em>：现在仓里没有一个 Mapper、没有 {@code @MapperScan}、没有 XML。
 * 它只回答一个此前只能靠猜的问题 —— <b>把 MyBatis-Plus 放进这个应用，两件事会不会互相挤掉</b>。
 * 风险表里那行"MyBatis 与 AirPower-JPA 并存 → 事务/一二级缓存语义不一致"（🔴 P0）说的就是这个，
 * 而它无法在本地验证（沙箱没有 JDK、也没有依赖树），所以把它做成一次可回归的断言，
 * 而不是"我看着应该没问题"。</p>
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
                "airpower.redis.enabled=false",
                "airpower.mqtt.enabled=false",
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
                "EntityManagerFactory 没了：JPA 读路径与全部 CURD 端点会一起塌");
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
                "事务管理器不是 JPA 那个：JPA 侧的 flush/一级缓存语义将不受 @Transactional 管");
    }

    /**
     * 本轮只验"并存"，不验"能改 SQL"。
     *
     * <p><b>已实测到的缺口（CI 抓的）</b>：{@code com.github.jsqlparser} 本体<em>没有</em>随
     * {@code mybatis-plus-jsqlparser:3.5.9} 传递到测试 classpath（{@code ClassNotFoundException:
     * com.github.jsqlparser.parser.CCJSqlParserUtil}）。这不影响本轮结论 —— 本应用现在一个
     * {@code InnerInterceptor} 都没注册，没有东西会去解析 SQL。但它是一条硬前置：
     * <b>接分页或数据权限插件之前，必须先把 jsqlparser 补齐</b>（CI 的依赖树那一步会把
     * {@code jsqlparser} 相关行打出来，定版本看它，别看记忆）。</p>
     */
    @Test
    @DisplayName("MyBatis-Plus 自己的类型可用，且当前一个插件都没注册（别把这一格读成「已经在改 SQL」）")
    void mybatisPlusIsUsableButNothingIsWired() throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        assertNotNull(interceptor, "MybatisPlusInterceptor 不可用：分页与数据权限插件都要挂在它上面");
        assertTrue(interceptor.getInterceptors().isEmpty(),
                "这一格的前提是零接线：一旦这里出现插件，上面那段关于 jsqlparser 缺口的注释就成了待爆的雷");
        assertNotNull(Class.forName("com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder"),
                "mybatis-plus-core 不在 classpath：starter 被 airpower 的依赖管理挤掉了");
    }

    @Test
    @DisplayName("MyBatis 能查到 JPA 那张表（同一库、同一 schema）")
    void mybatisReadsTheSameSchema() throws Exception {
        long viaJpa = ((Number) entityManager.createNativeQuery("select count(1) from data_resource")
                .getSingleResult()).longValue();
        long viaMyBatis;
        try (SqlSession session = sqlSessionFactory().openSession()) {
            try (Statement statement = session.getConnection().createStatement();
                 ResultSet rs = statement.executeQuery("select count(1) from data_resource")) {
                assertTrue(rs.next(), "COUNT 查询必须返回一行");
                viaMyBatis = rs.getLong(1);
            }
        }
        assertEquals(viaJpa, viaMyBatis,
                "两套 ORM 读到的行数不同：测试库里存在两份 schema 或两条连接指向不同库，换轨的对账前提不成立");
    }
}

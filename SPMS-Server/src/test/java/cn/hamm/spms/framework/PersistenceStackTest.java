package cn.hamm.spms.framework;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>持久化栈：只有一套 ORM（MyBatis-Plus），JPA 不许回来</h1>
 *
 * <p>本类原名 {@code MybatisJpaCoexistenceTest}，J2 之前它验的是"两套 ORM 能不能共存"
 * （P2 换轨的前提）；J4（2026-09-18）删掉 {@code spring-boot-starter-data-jpa} 之后，
 * <b>它验的是反向事实</b>：容器里没有 {@code EntityManagerFactory}、没有
 * {@code JpaTransactionManager}，事务管理器只有 {@link DataSourceTransactionManager} 一个。
 * 换判据不删测试 —— 这条测试的价值是"回滚位/共存位"的回归信号，现在它是"两套 ORM 不许回来"的回归信号。</p>
 *
 * <p>为什么用 {@code Class.forName} 而不是 {@code getBeanNamesForType(...)} 去数 JPA 的 Bean：
 * 那两个类型是<b>编译期就不该存在于 classpath 上</b>的（starter 已删），写成静态类型会让本类
 * 自己把 JPA 拉回来 —— 于是"JPA 不许回来"这句话在测试里先被违反一次。按名字探一次 classpath
 * 既说清了事实，也让回退（有人把 starter 加回来）立刻变红。</p>
 *
 * <p>档位刻意钉在 {@code kubo.data-permission.mode=off}：本类要验的是持久化栈的形状，
 * 掺进权限判定只会让失败原因分不清是谁（同 §19.29 的教训：<em>集成测试要显式声明它站在哪一档</em>）。</p>
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
@DisplayName("持久化栈 · 只有一套 ORM")
class PersistenceStackTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApplicationContext applicationContext;

    private SqlSessionFactory sqlSessionFactory() {
        SqlSessionFactory factory = applicationContext.getBeanProvider(SqlSessionFactory.class).getIfAvailable();
        assertNotNull(factory, "MyBatis-Plus 的自动配置没起来：starter 没生效或被条件挤掉了，整条读写链路都不成立");
        return factory;
    }

    @Test
    @DisplayName("JPA 已经整体退场：classpath 上连 EntityManagerFactory 与 JpaTransactionManager 都不该有")
    void jpaIsGoneForGood() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("jakarta.persistence.EntityManagerFactory"),
                "classpath 上又有 JPA 了：spring-boot-starter-data-jpa 被加回来了？"
                        + "本仓的实体/仓储/数据权限都只认 MyBatis-Plus，两套 ORM 并存会同时引入两套一级缓存与 flush 语义");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.orm.jpa.JpaTransactionManager"),
                "又出现了 JPA 事务管理器：@Transactional 会随机绑到其中一个，"
                        + "而且它管不到 MyBatis 的会话，flush 语义直接失真");
    }

    @Test
    @DisplayName("MyBatis 用的就是容器里那个 DataSource，不是自己新建的连接池")
    void mybatisReusesTheContainerDataSource() {
        assertSame(dataSource, sqlSessionFactory().getConfiguration().getEnvironment().getDataSource(),
                "两套连接池就是两套事务语义：@Transactional 对 Mapper 写入不生效，"
                        + "读写分开提交会造出「回滚了一份、另一份已落库」这类查不出来的脏数据");
    }

    @Test
    @DisplayName("全容器只有一个事务管理器，且是 DataSourceTransactionManager")
    void singleTransactionManagerIsDataSourceBacked() {
        String[] names = applicationContext.getBeanNamesForType(PlatformTransactionManager.class);
        assertEquals(1, names.length,
                "出现了多个事务管理器：@Transactional 会随机绑到其中一个，"
                        + "这类歧义在 J2 换轨期被专门清理过一次，别让它回来（names=" + String.join(",", names) + "）");
        assertInstanceOf(DataSourceTransactionManager.class, applicationContext.getBean(PlatformTransactionManager.class),
                "事务管理器不是数据源那个：MyBatis-Plus 的会话与它共用同一个连接才能保证「一个事务里一次提交」");
    }

    /**
     * 本轮只验「只有一套 ORM」，不验「JPQL 能不能跑」。
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
}

package cn.hamm.spms.module.personnel.user;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

/**
 * <h1>数据库连接信息</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface UserRepository extends BaseRepository<UserEntity> {
    /**
     * 根据邮箱查询一个用户
     *
     * @param email 邮箱
     * @return 用户
     */
    /**
     * 按邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户（不存在返回 {@code null}；多于一条直接抛，与 Spring Data 的派生查询同口径）
     * @apiNote J2：Spring Data 的派生查询名 → {@code default} + wrapper（详见
     * {@code PurchasePriceRepository#getBySupplierAndMaterial} 上的说明）
     */
    default UserEntity getByEmail(String email) {
        return selectOne(new QueryWrapper<UserEntity>().eq("email", email));
    }

    /**
     * 根据手机查询一个用户
     *
     * @param phone 手机
     * @return 用户
     */
    /**
     * 按手机号查询用户
     *
     * @param phone 手机号
     * @return 用户
     */
    default UserEntity getByPhone(String phone) {
        return selectOne(new QueryWrapper<UserEntity>().eq("phone", phone));
    }

    /**
     * 查"这些部门（及其子树）下的用户 ID"
     *
     * @param departmentIds 部门 ID 集合
     * @return 用户 ID 列表
     * @apiNote 用户与部门是多对多（中间表 {@code user_department_list}），wrapper 表达不了 join，
     * 也不好在 Java 里拼 EXISTS 子查询（本仓 SQL 只许住 mapper XML）—— 语句住在
     * {@code UserRepository.xml}，调用方拿到 ID 再 {@code in} 过滤。
     */
    List<Long> selectIdsByDepartmentIdIn(@Param("departmentIds") Collection<Long> departmentIds);
}

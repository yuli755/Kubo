package cn.hamm.spms.module.system.file;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * <h1>Repository</h1>
 *
 * @author Hamm.cn
 */
@Repository
public interface FileRepository extends BaseRepository<FileEntity> {
    /**
     * 根据文件类别和 MD5 查询文件
     *
     * @param category 文件类别
     * @param hashMd5  MD5
     * @return 文件
     */
    /**
     * 按分类 + 摘要查询（去重用）
     *
     * @param category 分类
     * @param hashMd5  摘要
     * @return 文件
     */
    default FileEntity getByCategoryAndHashMd5(Integer category, String hashMd5) {
        return selectOne(new QueryWrapper<FileEntity>().eq("category", category).eq("hash_md5", hashMd5));
    }

    /**
     * 根据文件类别和 MD5 查询全部文件行（阶段 D-4：同内容不同属主会各存一行，
     * 单值查询在多行下会抛 NonUniqueResultException，秒传改走列表再按属主筛选）
     *
     * @param category 文件类别
     * @param hashMd5  MD5
     * @return 文件列表
     */
    /**
     * 按分类 + 摘要查询列表
     *
     * @param category 分类
     * @param hashMd5  摘要
     * @return 文件列表
     */
    default List<FileEntity> getListByCategoryAndHashMd5(Integer category, String hashMd5) {
        return selectList(new QueryWrapper<FileEntity>().eq("category", category).eq("hash_md5", hashMd5));
    }
}

package cn.hamm.spms.module.system.file;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

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
    FileEntity getByCategoryAndHashMd5(Integer category, String hashMd5);

    /**
     * 根据文件类别和 MD5 查询全部文件行（阶段 D-4：同内容不同属主会各存一行，
     * 单值查询在多行下会抛 NonUniqueResultException，秒传改走列表再按属主筛选）
     *
     * @param category 文件类别
     * @param hashMd5  MD5
     * @return 文件列表
     */
    List<FileEntity> getListByCategoryAndHashMd5(Integer category, String hashMd5);
}

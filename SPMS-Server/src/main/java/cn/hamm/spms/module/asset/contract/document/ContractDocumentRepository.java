package cn.hamm.spms.module.asset.contract.document;

import cn.hamm.spms.base.BaseRepository;
import org.springframework.stereotype.Repository;

/**
 * <h1>合同附件数据源</h1>
 *
 * @author Hamm.cn
 * @apiNote J3：合同与附件是多对多（中间表 {@code contract_document_list}），回填对象图要按目标实体
 * 找仓储（{@code RelationHydrator} 的解析口径），而此前没有这个仓储 —— 于是
 * {@code ContractEntity.documentList} 只能永远是空集合。绑定的实体只影响 SQL 打到哪张表，
 * 语句仍住在 {@code ContractDocumentRepository.xml}。
 */
@Repository
public interface ContractDocumentRepository extends BaseRepository<ContractDocumentEntity> {
}

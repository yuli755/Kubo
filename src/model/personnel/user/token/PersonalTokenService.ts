import { AbstractBaseService } from '@/base/AbstractBaseService'
import { PersonalTokenEntity } from '@/model/personnel/user/token/PersonalTokenEntity'

/**
 * # 用户服务接口
 */
export class PersonalTokenService extends AbstractBaseService<PersonalTokenEntity> {
  baseUrl = 'personalToken'

  entityClass = PersonalTokenEntity

  async getMyPersonalTokenList(): Promise<PersonalTokenEntity[]> {
    const json = await this.api('getMyList').requestRaw()
    return PersonalTokenEntity.fromJsonArray(json)
  }

  async createMyPersonalToken(name: string): Promise<string> {
    const personalToken = new PersonalTokenEntity()
    personalToken.name = name
    // 后端这里返回的是**明文字符串**（不是包裹过的实体），故显式给 `requestRaw` 泛型参数
    return this.api('createMy').requestRaw<string>(personalToken)
  }

  async enableMyPersonalToken(item: PersonalTokenEntity) {
    await this.api('enableMy').request(item.copyOnlyId())
  }

  async disableMyPersonalToken(item: PersonalTokenEntity) {
    await this.api('disableMy').request(item.copyOnlyId())
  }
}

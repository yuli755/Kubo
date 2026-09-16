import { WebColor, WebEnum } from '@airpower/web'

export class PermissionTypeEnum extends WebEnum {
  static API = new PermissionTypeEnum(0, 'API权限')
    .setColor(WebColor.WARNING)
}

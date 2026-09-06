import { departmentApi } from './department'
import { roleApi } from './role'
import { userApi } from './user'

export { departmentApi, roleApi, userApi }

export const personnelApi = {
  department: departmentApi,
  role: roleApi,
  user: userApi,
}

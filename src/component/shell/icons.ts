/**
 * # 外壳图标映射
 *
 * 菜单的 `icon` 字段是后端下发的**字符串名**（如 `DataBoard`），前端需要把它解析成
 * Element Plus 图标组件。为了避免把 200+ 图标全打进包里，这里只显式引入「菜单可能用到」
 * 的一个受控子集，未命中时回退到 `Menu`：
 *
 * - 受控子集 → 体积可控（每个图标 gzip 后约 0.3–0.6 KB）；
 * - 回退而非报错 → 后端新增菜单时不会白屏，只是图标兜底。
 *
 * ⚠️ 新增菜单图标时在此登记；未登记的图标会显示为回退图标（不报错）。
 */

import type { Component } from 'vue'
import {
  Avatar,
  Bell,
  Box,
  Calendar,
  Coin,
  Collection,
  Connection,
  Coordinate,
  Cpu,
  DataBoard,
  Document,
  Download,
  Files,
  Filter,
  Goods,
  Grid,
  Guide,
  Hide,
  House,
  Key,
  Link,
  Lock,
  Management,
  Menu as MenuIcon,
  Money,
  Monitor,
  Notebook,
  Odometer,
  OfficeBuilding,
  Operation,
  Platform,
  Postcard,
  PriceTag,
  ScaleToOriginal,
  School,
  Sell,
  Setting,
  ShoppingBag,
  ShoppingCart,
  Sort,
  Tickets,
  Tools,
  Upload,
  User,
  UserFilled,
  Van,
  View,
  Warning,
} from '@element-plus/icons-vue'

const ICON_MAP: Record<string, Component> = {
  Avatar,
  Bell,
  Box,
  Calendar,
  Coin,
  Collection,
  Connection,
  Coordinate,
  Cpu,
  DataBoard,
  Document,
  Download,
  Files,
  Filter,
  Goods,
  Grid,
  Guide,
  Hide,
  House,
  Key,
  Link,
  Lock,
  Management,
  Menus: MenuIcon,
  Menu: MenuIcon,
  Money,
  Monitor,
  Notebook,
  Odometer,
  OfficeBuilding,
  Operation,
  Platform,
  Postcard,
  PriceTag,
  ScaleToOriginal,
  School,
  Sell,
  Setting,
  ShoppingBag,
  ShoppingCart,
  Sort,
  Tickets,
  Tools,
  Upload,
  User,
  UserFilled,
  Van,
  View,
  Warning,
}

/** 解析菜单图标名 → 组件（未知图标回退到 Menu） */
export function resolveMenuIcon(name?: string): Component {
  if (!name) {
    return MenuIcon
  }
  return ICON_MAP[name] ?? MenuIcon
}

/**
 * Element Plus 按需注册。
 *
 * 原先 main.js 直接 `app.use(ElementPlus)` 会把整套组件库打进产物，
 * 本项目实际只用到下面这些组件。逐个注册后 Rollup 可以摇掉其余代码，
 * 构建 JS 总量从约 2.8 MB 降到约 1.3 MB；配合 plugins/element-plus-css.js
 * 的按需样式，CSS 从 363 KB 降到 251 KB（gzip 48.9 → 32.0 KB）。
 *
 * 新增页面若使用了新组件，把组件名加进 COMPONENTS，
 * 并同步在 element-plus-css.js 里补一行样式 import。
 */
import {
  ElAlert, ElBadge, ElButton, ElButtonGroup, ElCol, ElConfigProvider,
  ElDatePicker, ElDialog, ElDivider, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElInputNumber,
  ElMenu, ElMenuItem, ElOption, ElPagination, ElPopover, ElRadio, ElRadioButton,
  ElRadioGroup, ElRate, ElRow, ElScrollbar, ElSelect, ElSwitch, ElTabPane,
  ElTable, ElTableColumn, ElTabs, ElTag, ElTimeSelect, ElTimeline,
  ElTimelineItem, ElTooltip
} from 'element-plus'

const COMPONENTS = [
  ElAlert, ElBadge, ElButton, ElButtonGroup, ElCol, ElConfigProvider,
  ElDatePicker, ElDialog, ElDivider, ElDrawer, ElDropdown, ElDropdownItem,
  ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElIcon, ElInput, ElInputNumber,
  ElMenu, ElMenuItem, ElOption, ElPagination, ElPopover, ElRadio, ElRadioButton,
  ElRadioGroup, ElRate, ElRow, ElScrollbar, ElSelect, ElSwitch, ElTabPane,
  ElTable, ElTableColumn, ElTabs, ElTag, ElTimeSelect, ElTimeline,
  ElTimelineItem, ElTooltip
]

/**
 * 模板里用字符串动态渲染的图标（菜单 icon、看板 icon）与少量未显式导入的图标。
 * 其余图标由各页面自行 `import { Plus } from '@element-plus/icons-vue'`。
 */
import {
  ArrowDown, Bell, Calendar, Clock, Cpu, DataLine, Document, InfoFilled,
  Location, Message, Reading, School, Setting, Stamp, SwitchButton,
  Tickets, Tools, User, Warning
} from '@element-plus/icons-vue'

const ICONS = {
  ArrowDown, Bell, Calendar, Clock, Cpu, DataLine, Document, InfoFilled,
  Location, Message, Reading, School, Setting, Stamp, SwitchButton,
  Tickets, Tools, User, Warning
}

export function setupElementPlus(app) {
  COMPONENTS.forEach((c) => app.use(c))
  Object.entries(ICONS).forEach(([name, component]) => app.component(name, component))
}

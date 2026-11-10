/**
 * Element Plus 样式按需引入。
 *
 * 原先 main.js 里 `import 'element-plus/dist/index.css'` 会把全部 118 个组件的
 * 样式（356 KB / gzip 约 48 KB）打包进产物，而本项目只用到下面 40 余个组件。
 * 每个 style/css.mjs 会自行 import 依赖组件与 base 样式，无需手动补全依赖。
 *
 * 维护约定：在 plugins/element-plus.js 里新增组件时，同步在这里加一行样式。
 */
import 'element-plus/es/components/base/style/css.mjs'

// 基础布局与数据录入
import 'element-plus/es/components/row/style/css.mjs'
import 'element-plus/es/components/col/style/css.mjs'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/button-group/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'
import 'element-plus/es/components/input-number/style/css.mjs'
import 'element-plus/es/components/select/style/css.mjs'
import 'element-plus/es/components/option/style/css.mjs'
import 'element-plus/es/components/date-picker/style/css.mjs'
import 'element-plus/es/components/time-select/style/css.mjs'
import 'element-plus/es/components/radio/style/css.mjs'
import 'element-plus/es/components/radio-button/style/css.mjs'
import 'element-plus/es/components/radio-group/style/css.mjs'
import 'element-plus/es/components/switch/style/css.mjs'
import 'element-plus/es/components/rate/style/css.mjs'
import 'element-plus/es/components/form/style/css.mjs'
import 'element-plus/es/components/form-item/style/css.mjs'

// 数据展示
import 'element-plus/es/components/table/style/css.mjs'
import 'element-plus/es/components/table-column/style/css.mjs'
import 'element-plus/es/components/tag/style/css.mjs'
import 'element-plus/es/components/badge/style/css.mjs'
import 'element-plus/es/components/pagination/style/css.mjs'
import 'element-plus/es/components/empty/style/css.mjs'
import 'element-plus/es/components/timeline/style/css.mjs'
import 'element-plus/es/components/timeline-item/style/css.mjs'
import 'element-plus/es/components/tabs/style/css.mjs'
import 'element-plus/es/components/tab-pane/style/css.mjs'
import 'element-plus/es/components/alert/style/css.mjs'

// 弹层与反馈
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/drawer/style/css.mjs'
import 'element-plus/es/components/dropdown/style/css.mjs'
import 'element-plus/es/components/dropdown-menu/style/css.mjs'
import 'element-plus/es/components/dropdown-item/style/css.mjs'
import 'element-plus/es/components/tooltip/style/css.mjs'
import 'element-plus/es/components/popover/style/css.mjs'
import 'element-plus/es/components/scrollbar/style/css.mjs'
import 'element-plus/es/components/divider/style/css.mjs'

// 导航与图标容器
import 'element-plus/es/components/menu/style/css.mjs'
import 'element-plus/es/components/menu-item/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/config-provider/style/css.mjs'

// 命令式调用：ElMessage / ElMessageBox / v-loading / ElNotification
// 这些不经过 app.use()，样式必须显式引入，否则弹窗无样式
import 'element-plus/es/components/message/style/css.mjs'
import 'element-plus/es/components/message-box/style/css.mjs'
import 'element-plus/es/components/loading/style/css.mjs'
import 'element-plus/es/components/notification/style/css.mjs'

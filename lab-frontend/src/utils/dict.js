export const RESERVATION_STATUS = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
  FINISHED: { label: '已完成', type: 'info' }
}

/** 签到状态：NONE 未签到 / CHECKED_IN 已签到 / NO_SHOW 爽约 */
export const CHECKIN_STATUS = {
  NONE: { label: '未签到', type: 'info' },
  CHECKED_IN: { label: '已签到', type: 'success' },
  NO_SHOW: { label: '爽约', type: 'danger' }
}

/** 站内信类型 */
export const MESSAGE_TYPE = {
  RESERVATION: { label: '预约', type: 'primary' },
  REPAIR: { label: '报修', type: 'warning' },
  CREDIT: { label: '信用分', type: 'success' },
  SYSTEM: { label: '系统', type: 'info' }
}

export const REPAIR_STATUS = {
  PENDING: { label: '待派单', type: 'danger' },
  ASSIGNED: { label: '已派单', type: 'warning' },
  REPAIRING: { label: '维修中', type: 'primary' },
  DONE: { label: '待验收', type: 'success' },
  VERIFIED: { label: '已完成', type: 'info' }
}

export const EQUIPMENT_STATUS = {
  IDLE: { label: '空闲', type: 'success' },
  IN_USE: { label: '使用中', type: 'primary' },
  REPAIR: { label: '维修中', type: 'warning' },
  SCRAPPED: { label: '已报废', type: 'info' }
}

export const URGENCY = {
  LOW: { label: '一般', type: 'info' },
  NORMAL: { label: '较急', type: 'warning' },
  HIGH: { label: '紧急', type: 'danger' }
}

export const ROLE_LABEL = {
  ADMIN: '系统管理员',
  TEACHER: '教师',
  STUDENT: '学生',
  REPAIRER: '维修员'
}

export const NOTICE_TYPE = {
  NOTICE: { label: '通知', type: 'primary' },
  MAINTENANCE: { label: '维护', type: 'warning' },
  SYSTEM: { label: '系统', type: 'info' }
}

export function labelOf(dict, key) {
  return dict[key]?.label || key || '-'
}

export function typeOf(dict, key) {
  return dict[key]?.type || ''
}

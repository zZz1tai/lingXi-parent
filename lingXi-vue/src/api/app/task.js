import request from '@/utils/request'

// 搜索工单
export function searchTasks(params) {
  return request({
    url: '/task-service/task/search',
    method: 'get',
    params
  })
}

// 获取用户排名
export function getUserRank(userId) {
  return request({
    url: `/task-service/task/rank/${userId}`,
    method: 'get'
  })
}

// 接受工单
export function acceptTask(taskId) {
  return request({
    url: `/task-service/task/accept/${taskId}`,
    method: 'get'
  })
}

// 取消工单
export function cancelTask(taskId, data) {
  return request({
    url: `/task-service/task/cancel/${taskId}`,
    method: 'post',
    data
  })
}

// 完成工单
export function completeTask(taskId) {
  return request({
    url: `/task-service/task/complete/${taskId}`,
    method: 'get'
  })
}

// 获取工单详情
export function getTaskDetails(taskId) {
  return request({
    url: `/task-service/taskDetails/${taskId}`,
    method: 'get'
  })
}

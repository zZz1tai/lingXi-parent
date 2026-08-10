import request from '@/utils/request'

/** 按项目查询生成任务列表（无分页）。 */
export function listAiVideoTask(projectId) {
  return request({
    url: '/aivideo/task/list',
    method: 'get',
    params: { projectId }
  })
}

/** 分页查询生成任务队列，支持 projectId/taskType/status 筛选。 */
export function pageAiVideoTask(query) {
  return request({
    url: '/aivideo/task/page',
    method: 'get',
    params: query
  })
}

/** 取消生成任务（排队/重试中直接取消，视频等待回调中取消为终态）。 */
export function cancelAiVideoTask(taskId) {
  return request({
    url: '/aivideo/task/' + taskId + '/cancel',
    method: 'post'
  })
}

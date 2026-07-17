import request from '@/utils/request'

export function listAiVideoProject(query) {
  return request({
    url: '/aivideo/project/list',
    method: 'get',
    params: query
  })
}

export function getAiVideoProject(projectId) {
  return request({
    url: '/aivideo/project/' + projectId,
    method: 'get'
  })
}

export function addAiVideoProject(data) {
  return request({
    url: '/aivideo/project',
    method: 'post',
    data: data
  })
}

export function updateAiVideoProject(data) {
  return request({
    url: '/aivideo/project',
    method: 'put',
    data: data
  })
}

export function delAiVideoProject(projectId) {
  return request({
    url: '/aivideo/project/' + projectId,
    method: 'delete'
  })
}

export function listAiVideoChapter(projectId) {
  return request({
    url: '/aivideo/project/' + projectId + '/chapter/list',
    method: 'get'
  })
}

export function addAiVideoChapter(projectId, data) {
  return request({
    url: '/aivideo/project/' + projectId + '/chapter',
    method: 'post',
    data: data
  })
}

export function delAiVideoChapter(projectId, chapterId) {
  return request({
    url: '/aivideo/project/' + projectId + '/chapter/' + chapterId,
    method: 'delete'
  })
}

export function analyzeAiVideoChapter(projectId, chapterId) {
  return request({
    url: '/aivideo/project/' + projectId + '/chapter/' + chapterId + '/analyze',
    method: 'post'
  })
}

export function getAiVideoStoryBible(projectId, chapterId) {
  return request({
    url: '/aivideo/project/' + projectId + '/chapter/' + chapterId + '/story-bible',
    method: 'get'
  })
}

export function listAiVideoAsset(query) {
  return request({
    url: '/aivideo/asset/list',
    method: 'get',
    params: query
  })
}

export function approveAiVideoAsset(assetId) {
  return request({
    url: '/aivideo/asset/' + assetId + '/approve',
    method: 'post'
  })
}

export function updateAiVideoAssetPrompt(assetId, data) {
  return request({
    url: '/aivideo/asset/' + assetId + '/prompt',
    method: 'put',
    data
  })
}

export function generateAiVideoAssetImage(assetId) {
  return request({
    url: '/aivideo/asset/' + assetId + '/generate-image',
    method: 'post'
  })
}

export function retryAiVideoAssetImage(assetId) {
  return request({
    url: '/aivideo/asset/' + assetId + '/retry-image',
    method: 'post'
  })
}

export function createAiVideoAssetVideoDraft(keyframeId) {
  return request({
    url: '/aivideo/asset/' + keyframeId + '/video-draft',
    method: 'post'
  })
}

export function updateAiVideoAssetVideoPrompt(assetId, data) {
  return request({
    url: '/aivideo/asset/' + assetId + '/video-prompt',
    method: 'put',
    data
  })
}

export function generateAiVideoAssetVideo(assetId) {
  return request({
    url: '/aivideo/asset/' + assetId + '/video',
    method: 'post'
  })
}

export function createAiVideoAssetRegenerationDraft(assetId, data) {
  return request({
    url: '/aivideo/asset/' + assetId + '/regeneration-draft',
    method: 'post',
    data
  })
}

export function delAiVideoAsset(assetId) {
  return request({
    url: '/aivideo/asset/' + assetId,
    method: 'delete'
  })
}

export function resolveAiVideoAssetWanxSubmission(assetId, data) {
  return request({
    url: '/aivideo/asset/' + assetId + '/video-resolution',
    method: 'post',
    data
  })
}

export function listAiVideoTask(projectId) {
  return request({
    url: '/aivideo/task/list',
    method: 'get',
    params: { projectId }
  })
}

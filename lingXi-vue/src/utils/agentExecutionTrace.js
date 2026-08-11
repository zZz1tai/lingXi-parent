const KNOWN_ACTIVITY_STATUSES = new Set(['running', 'completed', 'error']);

export const normalizeAgentActivityStatus = status => (
  KNOWN_ACTIVITY_STATUSES.has(status) ? status : 'pending'
);

export const getAgentExecutionTraceState = (activities = []) => {
  const items = Array.isArray(activities) ? activities : [];
  const counts = items.reduce((result, activity) => {
    result[normalizeAgentActivityStatus(activity?.status)] += 1;
    return result;
  }, { running: 0, completed: 0, error: 0, pending: 0 });

  let status = 'completed';
  if (counts.error) status = 'error';
  else if (counts.running || counts.pending) status = 'running';

  let summaryText = `已完成 ${items.length} 个步骤`;
  if (status === 'error') summaryText = `${counts.error} 个步骤未完成`;
  if (status === 'running') {
    summaryText = `正在执行 ${counts.running + counts.pending} 个步骤`;
  }

  return {
    counts,
    status,
    summaryText,
    expandedByDefault: status !== 'completed',
    stateKey: `${status}:${items.length}:${counts.error}`
  };
};

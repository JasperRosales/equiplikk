const BASE = '/api';

async function request(path, options = {}) {
  const config = { credentials: 'same-origin', ...options };
  config.headers = { ...(options.headers || {}) };
  let body = options.body;
  if (body !== undefined && body !== null && !(body instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body);
  }
  config.body = body;

  let response;
  try {
    response = await fetch(BASE + path, config);
  } catch (error) {
    throw new Error('Cannot reach the EquipLink server. Check your connection.');
  }

  const type = response.headers.get('content-type') || '';
  const payload = response.status === 204
    ? null
    : type.includes('json') ? await response.json() : await response.text();

  if (!response.ok) {
    const message = payload && typeof payload === 'object'
      ? (payload.error || payload.message) : payload;
    const error = new Error(message || `Request failed (${response.status}).`);
    error.status = response.status;
    throw error;
  }
  return payload;
}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  del: (path, body) => request(path, { method: 'DELETE', body }),

  login: (username, password) => api.post('/auth/login', { username, password }),
  register: (data) => api.post('/auth/register', data),
  logout: () => api.post('/auth/logout'),
  me: () => api.get('/auth/me'),
  requestPasswordReset: (usernameOrEmail, registeredEmail) =>
    api.post('/auth/password-request', { usernameOrEmail, registeredEmail }),
  completePasswordReset: (data) => api.post('/auth/password-request/complete', data),

  dashboard: () => api.get('/dashboard'),
  venues: () => api.get('/venues'),
  saveVenues: (venues) => api.put('/venues', { venues }),

  equipment: (scope = '') => api.get('/equipment' + (scope ? `?scope=${encodeURIComponent(scope)}` : '')),
  equipmentCounters: () => api.get('/equipment/counters'),
  availableForSchedule: (date, start, end, exclude = 0) =>
    api.get(`/equipment/available-for-schedule?date=${date}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&exclude=${exclude}`),
  saveEquipment: (data, id) => id ? api.put(`/equipment/${id}`, data) : api.post('/equipment', data),
  deleteEquipment: (id) => api.del(`/equipment/${id}`),
  equipmentUsage: (id) => api.get(`/equipment/${id}/usage`),

  createRequest: (form) => api.post('/requests', form),
  updateRequest: (id, form) => api.put(`/requests/${id}`, form),
  deleteRequest: (id) => api.del(`/requests/${id}`),
  restoreRequest: (id) => api.post(`/requests/${id}/restore`),
  activeRequests: () => api.get('/requests/active'),
  requestHistory: () => api.get('/requests/history'),
  allRequests: () => api.get('/requests/all'),
  queue: () => api.get('/requests/queue'),
  schedule: () => api.get('/requests/schedule'),
  transactions: () => api.get('/requests/transactions'),
  roomConflicts: () => api.get('/requests/room-conflicts'),
  request: (id) => api.get(`/requests/${id}`),
  requestAudit: (id) => api.get(`/requests/${id}/history`),
  requestAvailability: (id) => api.get(`/requests/${id}/availability`),
  assignmentAvailability: (id) => api.get(`/requests/${id}/assignment-availability`),
  assign: (id, assignments) => api.post(`/requests/${id}/assign`, { assignments }),
  decline: (id, remarks) => api.post(`/requests/${id}/decline`, { remarks }),
  needsAction: (id, remarks) => api.post(`/requests/${id}/needs-action`, { remarks }),
  release: (id, groupId, conditions) => api.post(`/requests/${id}/release`, { groupId, conditions }),
  returned: (id, inspections) => api.post(`/requests/${id}/return`, { inspections }),
  receive: (id) => api.post(`/requests/${id}/receive`),
  adminReview: (id, approve, remarks) => api.post(`/requests/${id}/admin-review`, { approve, remarks }),
  directorReview: (id, approve, remarks) => api.post(`/requests/${id}/director-review`, { approve, remarks }),
  exportForm: (id) => `${BASE}/export/request-form/${id}`,

  notifications: () => api.get('/notifications'),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: (id) => api.post(`/notifications/${id}/read`),
  markAllRead: () => api.post('/notifications/read-all'),
  deleteNotification: (id) => api.del(`/notifications/${id}`),
  restoreNotification: (id) => api.post(`/notifications/${id}/restore`),
  deleteAllNotifications: () => api.post('/notifications/delete-all'),
  restoreAllNotifications: (ids) => api.post('/notifications/restore-all', { ids }),
  sweepNotifications: () => api.post('/notifications/sweep'),

  borrowers: () => api.get('/users/borrowers'),
  createFaculty: (data) => api.post('/users/faculty', data),
  setBorrowerActive: (id, active) => api.post(`/users/${id}/active`, { active }),
  deleteBorrower: (id) => api.del(`/users/${id}`),
  restoreBorrower: (id) => api.post(`/users/${id}/restore`),
  accountDetails: (id) => api.get(`/users/${id}/account`),
  passwordRequests: () => api.get('/password-requests'),
  reviewPasswordRequest: (id, approve, note) => api.post(`/password-requests/${id}/review`, { approve, note }),

  profile: () => api.get('/profile'),
  updateProfile: (data) => api.put('/profile', data),
  updateNotificationPref: (enabled) => api.put('/profile/notifications', { enabled }),
  updateSoundPref: (enabled, sound) => api.put('/profile/sound', { enabled, sound }),
  pictureUrl: () => `${BASE}/profile/picture`,
  meta: () => api.get('/meta')
};

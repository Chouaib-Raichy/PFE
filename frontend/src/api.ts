import type { Project, ProjectInput, ProjectStatus, Stats, Task } from './types'

const BASE = '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    ...init,
  })
  if (!res.ok) {
    let message = `Erreur ${res.status}`
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch {
      /* corps non JSON */
    }
    throw new Error(message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const api = {
  listProjects(q: string, status: ProjectStatus | ''): Promise<Project[]> {
    const params = new URLSearchParams()
    if (q.trim()) params.set('q', q.trim())
    if (status) params.set('status', status)
    const query = params.toString()
    return request<Project[]>(`/projects${query ? `?${query}` : ''}`)
  },
  stats(): Promise<Stats> {
    return request<Stats>('/stats')
  },
  createProject(input: ProjectInput): Promise<Project> {
    return request<Project>('/projects', { method: 'POST', body: JSON.stringify(input) })
  },
  updateProject(id: number, input: ProjectInput): Promise<Project> {
    return request<Project>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  },
  deleteProject(id: number): Promise<void> {
    return request<void>(`/projects/${id}`, { method: 'DELETE' })
  },
  addTask(projectId: number, label: string): Promise<Task> {
    return request<Task>(`/projects/${projectId}/tasks`, {
      method: 'POST',
      body: JSON.stringify({ label }),
    })
  },
  toggleTask(taskId: number): Promise<Task> {
    return request<Task>(`/tasks/${taskId}/toggle`, { method: 'POST' })
  },
}
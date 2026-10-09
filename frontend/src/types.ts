export type ProjectStatus = 'EN_COURS' | 'SOUTENU' | 'VALIDE'

export const STATUS_LABELS: Record<ProjectStatus, string> = {
  EN_COURS: 'En cours',
  SOUTENU: 'Soutenu',
  VALIDE: 'Validé',
}

export interface Task {
  id: number
  label: string
  done: boolean
}

export interface Project {
  id: number
  title: string
  student: string
  supervisor: string
  status: ProjectStatus
  description: string
  defenseDate: string | null
  tasks: Task[]
  doneTasks: number
}

export interface Stats {
  projects: number
  tasks: number
  done: number
}

export interface ProjectInput {
  title: string
  student: string
  supervisor: string
  status?: ProjectStatus
  description?: string
  defenseDate?: string | null
}
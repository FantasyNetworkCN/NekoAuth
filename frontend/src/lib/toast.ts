import { reactive } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  message: string
  kind: ToastKind
}

export const toasts = reactive<ToastItem[]>([])
let nextId = 1

export function showToast(message: string, kind: ToastKind = 'info', timeout = 3600) {
  const id = nextId++
  toasts.push({ id, message, kind })
  window.setTimeout(() => dismissToast(id), timeout)
}

export function dismissToast(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

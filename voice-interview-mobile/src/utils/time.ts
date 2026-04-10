export const formatDurationMs = (durationMs: number) => {
  if (!durationMs || durationMs < 1000) {
    return '00:00'
  }
  const totalSeconds = Math.round(durationMs / 1000)
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

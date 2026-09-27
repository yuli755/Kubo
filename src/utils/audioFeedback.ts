/**
 * 仓储手持机 / 扫码枪工业级声音与震动反馈工具
 *
 * 在嘈杂的工业仓储与作业现场，通过 Web Audio API 合成轻量音频并触发震动，
 * 无需引入外部静态音频资源（0 KB 外部文件），适配主流扫码手持机与移动终端。
 */

interface WebAudioWindow extends Window {
  webkitAudioContext?: typeof AudioContext
}

function getAudioContext(): AudioContext | null {
  try {
    const audioWin = window as unknown as WebAudioWindow
    const AudioCtx = window.AudioContext || audioWin.webkitAudioContext
    if (!AudioCtx) {
      return null
    }
    return new AudioCtx()
  }
  catch {
    return null
  }
}

/**
 * 扫码成功：880Hz 短促清脆高音 (A5)，伴随 60ms 轻微触觉震动
 */
export function playScanSuccess(): void {
  try {
    const ctx = getAudioContext()
    if (ctx) {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.setValueAtTime(880, ctx.currentTime)
      gain.gain.setValueAtTime(0.12, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start(ctx.currentTime)
      osc.stop(ctx.currentTime + 0.1)
    }
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate(60)
    }
  }
  catch {
    // 静默降级：避免未授权或环境限制抛错
  }
}

/**
 * 扫码预警 / 重复扫码：520Hz 中频双音，伴随双震
 */
export function playScanWarning(): void {
  try {
    const ctx = getAudioContext()
    if (ctx) {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'triangle'
      osc.frequency.setValueAtTime(520, ctx.currentTime)
      gain.gain.setValueAtTime(0.15, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.18)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start(ctx.currentTime)
      osc.stop(ctx.currentTime + 0.18)
    }
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate([40, 40, 40])
    }
  }
  catch {
    // 静默降级
  }
}

/**
 * 扫码失败 / 异常：260Hz 低频警报，伴随长震
 */
export function playScanError(): void {
  try {
    const ctx = getAudioContext()
    if (ctx) {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sawtooth'
      osc.frequency.setValueAtTime(260, ctx.currentTime)
      gain.gain.setValueAtTime(0.18, ctx.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.28)
      osc.connect(gain)
      gain.connect(ctx.destination)
      osc.start(ctx.currentTime)
      osc.stop(ctx.currentTime + 0.28)
    }
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      navigator.vibrate([120, 60, 120])
    }
  }
  catch {
    // 静默降级
  }
}

/**
 * 仓储手持机 / 扫码枪工业级声音与震动反馈工具
 *
 * 在嘈杂的工业仓储与作业现场，通过 Web Audio API 合成轻量音频并触发震动，
 * 无需引入外部静态音频资源（0 KB 外部文件），适配主流扫码手持机与移动终端。
 */

interface WebAudioWindow extends Window {
  webkitAudioContext?: typeof AudioContext
}

/**
 * 模块级单例：`new AudioContext()` **每个都要占一个系统音频资源**，浏览器对同时
 * 存在的上下文数量有上限（Safari/iOS 约 4~6 个，超限直接抛错或被静音）。
 * 此前每次响铃都新建一个且从不 close —— 扫码员连续扫一百次之后，设备就再也
 * 听不见成功/失败音了，而这恰恰是这套反馈机制存在的唯一理由。
 */
let sharedContext: AudioContext | null = null

function getAudioContext(): AudioContext | null {
  try {
    if (sharedContext) {
      // iOS/Safari 在页面切后台后会挂起上下文；恢复失败说明上下文已失效，重建一次
      if (sharedContext.state === 'suspended') {
        void sharedContext.resume().catch(() => {})
      }
      return sharedContext
    }
    const audioWin = window as unknown as WebAudioWindow
    const AudioCtx = window.AudioContext || audioWin.webkitAudioContext
    if (!AudioCtx) {
      return null
    }
    sharedContext = new AudioCtx()
    return sharedContext
  }
  catch {
    sharedContext = null
    return null
  }
}

/** 合成并播放一次短音（失败静默降级，绝不影响业务操作） */
function playTone(type: OscillatorType, hz: number, peak: number, durationS: number): void {
  try {
    const ctx = getAudioContext()
    if (!ctx) {
      return
    }
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = type
    osc.frequency.setValueAtTime(hz, ctx.currentTime)
    gain.gain.setValueAtTime(peak, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + durationS)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + durationS)
  }
  catch {
    // 静默降级：避免未授权或环境限制抛错
  }
}

function vibrate(pattern: number | number[]): void {
  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    navigator.vibrate(pattern)
  }
}

/**
 * 扫码成功：880Hz 短促清脆高音 (A5)，伴随 60ms 轻微触觉震动
 */
export function playScanSuccess(): void {
  playTone('sine', 880, 0.12, 0.1)
  vibrate(60)
}

/**
 * 扫码预警 / 重复扫码：520Hz 中频双音，伴随双震
 */
export function playScanWarning(): void {
  playTone('triangle', 520, 0.15, 0.18)
  vibrate([40, 40, 40])
}

/**
 * 扫码失败 / 异常：260Hz 低频警报，伴随长震
 */
export function playScanError(): void {
  playTone('sawtooth', 260, 0.18, 0.28)
  vibrate([120, 60, 120])
}

/** 仅供测试：重置单例（jsdom 里每个用例想换一个假 AudioContext 时用） */
export function __resetAudioContextForTests(): void {
  sharedContext = null
}

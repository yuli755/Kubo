/** 框架的契约：只说"我需要什么"，不认识应用面 */
export interface HttpTransport {
  request: (config: unknown) => Promise<unknown>
}

let transport: HttpTransport | null = null

export function configureTransport(next: HttpTransport | null): void {
  transport = next
}

export function getTransport(): HttpTransport {
  if (!transport) {
    throw new Error('未装配')
  }
  return transport
}

# Alertmanager 生产配置模板（env 渲染 · 零手动改配置）
# ============================================================================
# 渲染方：docker-compose.prod.yml 的 alertmanager 服务在容器启动时用 busybox sed
# 把占位符替换为 .env 注入的真实值后写出 rendered 配置，全程不需要人改 YAML。
#
# 占位符与 .env 变量对应关系（见 .env.production.example 告警段）：
#   __KUBO_ALERT_WEBHOOK_URL__  <- KUBO_ALERT_WEBHOOK_URL  （钉钉/企微/飞书机器人
#       或自研告警网关地址；注意群机器人通常需要 prometheus-webhook-dingtalk 之类
#       适配器，或直接填自研网关 URL。未配置时回落 example.invalid，投递失败但服务不崩）
global:
  resolve_timeout: 5m
  smtp_require_tls: false

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'kubo-webhook'
  routes:
    # critical 告警快车道：10s 分组、30m 重发，值班第一时间知道
    - matchers:
        - severity = "critical"
      group_wait: 10s
      repeat_interval: 30m
      receiver: 'kubo-webhook'

receivers:
  - name: 'kubo-webhook'
    webhook_configs:
      - url: '__KUBO_ALERT_WEBHOOK_URL__'
        send_resolved: true

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['instance']

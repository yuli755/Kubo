<script lang="ts" setup>
import { AEmpty, AGroup, APanel, DialogUtil, QueryRequest, WebColor } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { usePolling } from '@/hook'
import { DeviceService } from '@/hook/asset/useDevice'
import { AlarmStatusEnum } from '@/model/asset/device/AlarmStatusEnum'
import { DeviceEntity } from '@/model/asset/device/DeviceEntity'
import { DeviceStatusEnum } from '@/model/asset/device/DeviceStatusEnum'
import { reportError } from '@/utils/errorHandler'
import { DeviceMonitor } from '../../asset/device/component'

const deviceList = ref<DeviceEntity[]>([])

const request = ref(new QueryRequest(DeviceEntity))
request.value.filter = new DeviceEntity()
request.value.filter.isReporting = true

async function getDeviceList() {
  try {
    deviceList.value = await DeviceService.create().getList(request.value)
    // polling 在下方 usePolling(...) 创建；回调只在挂载后执行，无 TDZ 风险
    // eslint-disable-next-line ts/no-use-before-define
    polling.markSuccess()
  }
  catch (error) {
    reportError(error, 'device-list')
    // polling 在下方 usePolling(...) 创建；回调只在挂载后执行，无 TDZ 风险
    // eslint-disable-next-line ts/no-use-before-define
    polling.markFail()
  }
}

const polling = usePolling(getDeviceList, 1000, {
  maxRetries: 5,
  onOffline: () => {
    ElMessage.warning('设备状态连接中断，请检查网络或后端服务')
  },
  onOnline: () => {
    ElMessage.success('设备状态已恢复实时更新')
  },
})

polling.start()

function showMonitor(device: DeviceEntity) {
  DialogUtil.show(DeviceMonitor, device)
}
</script>

<template>
  <APanel>
    <AGroup
      :column="1"
      disable-collapse
      title="设备实时状态"
    >
      <el-alert
        v-if="polling.isOffline.value"
        title="设备数据连接中断"
        description="已连续多次获取设备数据失败，请检查网络连接或后端服务状态"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="device-view">
        <div
          v-for="item in DeviceStatusEnum.toArray()"
          :key="item.key"
          :style="{ width: `${deviceList.length > 0 ? (deviceList.filter((i) => i.status === item.key).length / deviceList.length) * 100 : 0}%` }"
          class="device-item"
        >
          <div
            :style="{ background: item.color }"
            class="device-card"
          >
            <div class="device-status">
              <span :style="{ backgroundColor: item.color }" />{{ item.label }}
            </div>
            <div class="device-number">
              {{ deviceList.filter((i) => i.status === item.key).length }}
            </div>
          </div>
        </div>
      </div>
      <div
        v-if="deviceList.length > 0"
        class="device-list"
      >
        <div
          v-for="device in deviceList"
          :key="device.id"
          class="device-item"
        >
          <div
            class="device-card"
            @click="showMonitor(device)"
          >
            <div
              :style="{ backgroundColor: DeviceStatusEnum.get(device.status)?.color || WebColor.WARNING }"
              class="bg"
            />
            <div class="device-header">
              <div class="device-name">
                {{ device.name }}
              </div>
              <div class="device-code">
                {{ device.code }}
              </div>
            </div>
            <div class="device-body">
              <div
                :style="{ color: DeviceStatusEnum.get(device.status)?.color || WebColor.NORMAL }"
                class="device-partcnt"
              >
                {{ device.partCount }}

                <span>产量</span>
              </div>
              <div class="device-info">
                <div
                  v-if="device.status === DeviceStatusEnum.ALARM.key && device.alarm !== AlarmStatusEnum.NONE.key"
                  :style="{
                    borderColor: AlarmStatusEnum.get(device.alarm)?.color || WebColor.WARNING,
                    color: AlarmStatusEnum.get(device.alarm)?.color || WebColor.WARNING,
                  }"
                  class="device-status"
                >
                  <span :style="{ backgroundColor: AlarmStatusEnum.get(device.alarm)?.color || WebColor.WARNING }" />
                  {{ AlarmStatusEnum.getLabel(device.alarm, '未知') }}
                </div>

                <div
                  :style="{
                    borderColor: DeviceStatusEnum.get(device.status)?.color || WebColor.WARNING,
                    color: DeviceStatusEnum.get(device.status)?.color || WebColor.WARNING,
                  }"
                  class="device-status"
                >
                  <span :style="{ backgroundColor: DeviceStatusEnum.get(device.status)?.color || WebColor.WARNING }" />
                  {{ DeviceStatusEnum.getLabel(device.status, '未知') }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <AEmpty v-else>
        设备加载中,请稍后...
      </AEmpty>
    </AGroup>
  </APanel>
</template>

<style lang="scss" scoped>
.device-view {
  margin: 0 8px;
  overflow: hidden;

  .device-item {
    display: inline-block;
    overflow: hidden;
    height: 100%;

    .device-card {
      overflow: hidden;
      padding: 2px 0;
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
      color: white;
      text-shadow: 0 1px 5px rgba($color: #000, $alpha: 0.3);
      text-align: center;
      font-size: 12px;

      .device-status {
        display: inline-block;
      }

      .device-number {
        font-weight: bold;
      }
    }
  }
}

.device-list {
  .device-item {
    width: 25%;
    display: inline-block;
    min-width: 360px;

    .device-card {
      background-color: var(--kubo-color-muted-bg);
      margin: 5px;
      border-radius: 10px;
      padding: 20px;
      cursor: pointer;
      z-index: 0;
      transition: all 0.3s;
      position: relative;
      overflow: hidden;
      text-shadow: 0 1px 1px rgba($color: #fff, $alpha: 1);

      .bg {
        position: absolute;
        left: 0;
        right: 0;
        top: 0;
        bottom: 0;
        z-index: -1;
        opacity: 0.1;
      }

      .device-header {
        display: flex;
        flex-direction: row;
        align-items: flex-start;

        .device-name {
          font-size: 18px;
          flex: 1;
          width: 0;
        }

        .device-code {
          font-size: 14px;
          color: var(--el-text-color-secondary);
        }
      }

      .device-body {
        display: flex;
        flex-direction: row;
        align-items: flex-end;

        .device-partcnt {
          font-size: 24px;
          font-weight: bolder;
          margin-top: 20px;
          color: #ff4500;
          flex: 1;

          span {
            font-size: 12px;
            color: var(--el-text-color-secondary);
            font-weight: normal;
            margin-left: -5px;
          }
        }

        .device-info {
          display: flex;
          flex-direction: row;
          align-items: center;

          .device-status {
            margin-left: 5px;
            display: flex;
            flex-direction: row;
            align-items: center;
            border: 1px solid transparent;
            padding: 0 3px;
            border-radius: 5px;
            font-size: 13px;

            span {
              width: 10px;
              height: 10px;
              border-radius: 100%;
              display: inline-block;
              margin-right: 5px;
            }
          }
        }
      }
    }

    .device-card:hover {
      transform: scale(1.05);
      box-shadow: 0 0 10px rgba($color: #000000, $alpha: 0.1);
      z-index: 1;
    }
  }
}
</style>

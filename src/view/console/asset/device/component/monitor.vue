<script lang="ts" setup>
import type { CollectionEntity } from '@/model/iot/collection/CollectionEntity'
import { ADialog, AEmpty, DialogProps, DialogUtil, WebColor } from '@airpower/web'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { usePolling } from '@/hook'
import { DeviceService } from '@/hook/asset/useDevice'
import { AlarmStatusEnum } from '@/model/asset/device/AlarmStatusEnum'
import { DeviceEntity } from '@/model/asset/device/DeviceEntity'
import { DeviceStatusEnum } from '@/model/asset/device/DeviceStatusEnum'
import { CollectionDefault } from '@/model/iot/collection/CollectionDefault'
import { reportError } from '@/utils/errorHandler'
import { ParameterEditor } from '@/view/console/iot/parameter/component'
import DeviceCollectHistory from './history.vue'

const props = defineProps(DialogProps.withParam(new DeviceEntity()))

const formData = ref(props.param.copy())

const monitorList = ref<CollectionEntity[]>([])

async function getDetail() {
  try {
    formData.value = await DeviceService.create().getDetail(props.param.id)
  }
  catch (error) {
    reportError(error, 'device-detail')
  }
}

async function getCurrentReport() {
  try {
    // polling 在下方定义；回调只在挂载后执行，不存在 TDZ 问题
    monitorList.value = await DeviceService.create().getCurrentReport(props.param.id)
    // polling 在下方 usePolling(...) 创建；回调只在挂载后执行，无 TDZ 风险
    // eslint-disable-next-line ts/no-use-before-define
    polling.markSuccess()
  }
  catch (error) {
    reportError(error, 'device-realtime')
    // polling 在下方 usePolling(...) 创建；回调只在挂载后执行，无 TDZ 风险
    // eslint-disable-next-line ts/no-use-before-define
    polling.markFail()
  }
}

const polling = usePolling(getCurrentReport, 1000, {
  maxRetries: 5,
  onOffline: () => {
    ElMessage.warning('设备实时数据连接中断，请检查网络或设备状态')
  },
  onOnline: () => {
    ElMessage.success('设备实时数据已恢复')
  },
})

polling.start()

getDetail()

function getValue(item: CollectionEntity) {
  switch (item.code) {
    case CollectionDefault.STATUS:
      return DeviceStatusEnum.getLabel(Number.parseInt(item.value, 10))
    case CollectionDefault.ALARM:
      return AlarmStatusEnum.getLabel(Number.parseInt(item.value, 10))
    default:
      return item.value
  }
}

function getColor(item: CollectionEntity) {
  switch (item.code) {
    case CollectionDefault.STATUS:
      return DeviceStatusEnum.get(Number.parseInt(item.value, 10))?.color || WebColor.NORMAL
    case CollectionDefault.ALARM:
      return AlarmStatusEnum.get(Number.parseInt(item.value, 10))?.color || WebColor.NORMAL
    default:
      return item.value
  }
}

function addParameter() {
  DialogUtil.show(ParameterEditor)
}

async function showHistory(item: CollectionEntity) {
  item.uuid = props.param.uuid
  polling.pause()
  try {
    await DialogUtil.show(DeviceCollectHistory, item)
  }
  finally {
    polling.resume()
  }
}
</script>

<template>
  <ADialog
    :title="`${formData.name}(${formData.code}) 采集实时监控`"
    height="70%"
    hide-confirm
    width="70%"
    @cancel="onCancel"
  >
    <el-alert
      v-if="polling.isOffline.value"
      title="实时数据连接中断"
      description="已连续多次获取数据失败，请检查网络连接或设备状态"
      type="warning"
      :closable="false"
      show-icon
    />
    <div
      v-if="monitorList.length > 0"
      class="monitor-list"
    >
      <div
        v-for="item in monitorList"
        :key="item.code"
        class="monitor-card"
      >
        <div
          class="card-body"
          @click="showHistory(item)"
        >
          <div class="head">
            <div class="title">
              {{ item.label }}
            </div>
            <div class="code">
              {{ item.code }}
            </div>
          </div>
          <div class="value">
            <div
              v-if="[CollectionDefault.ALARM.toString(), CollectionDefault.STATUS.toString()].includes(item.code)"
              :style="{ backgroundColor: getColor(item) }"
              class="light"
            />
            {{ getValue(item) }}
          </div>
        </div>
      </div>
    </div>
    <AEmpty v-else>
      暂无已添加的监控属性
      <el-link @click="addParameter()">
        去添加
      </el-link>
    </AEmpty>
  </ADialog>
</template>

<style lang="scss" scoped>
.monitor-list {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  flex-wrap: wrap;

  .monitor-card {
    display: inline-block;
    width: 33.33333333%;
    min-width: 360px;

    .card-body {
      cursor: pointer;
      transition: all 0.3s;
      margin: 5px;
      background-color: var(--kubo-color-muted-bg);
      border-radius: 10px;
      padding: 20px;
      height: 80px;
      vertical-align: top;

      .head {
        display: flex;
        flex-direction: row;

        .title {
          flex: 1;
          font-size: 16px;
        }

        .code {
          color: var(--el-text-color-secondary);
          font-size: 14px;
        }
      }

      .value {
        font-weight: bold;
        font-size: 20px;
        margin-top: 20px;
        display: flex;
        flex-direction: row;
        align-items: center;
        overflow: hidden;
        text-overflow: ellipsis;
        word-break: keep-all;
        white-space: nowrap;

        .light {
          width: 20px;
          height: 20px;
          border-radius: 100%;
          margin-right: 5px;
        }
      }
    }

    .card-body:hover {
      transform: scale(1.05);
      z-index: 1;
      box-shadow: 0 0 10px rgba($color: #000000, $alpha: 0.2);
    }
  }
}
</style>

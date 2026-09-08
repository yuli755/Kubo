<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { UserService } from '@/hook/personnel/useUser'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserEditor } from '.'

const props = defineProps(DialogProps.withSelector<UserEntity>())
</script>

<template>
  <ASelector
    :editor="UserEditor"
    :entity="UserEntity"
    :props="props"
    :service="UserService"
  />
</template>

<style lang="scss" scoped></style>

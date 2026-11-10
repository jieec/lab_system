<template>
  <div ref="chartRef" class="chart" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
// 按需注册：只引入看板用到的折线/柱状/饼图与网格、提示、图例组件，
// 不再打包整包 echarts。
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' }
})

const chartRef = ref()
let chart = null
let observer = null

function render() {
  if (!chart) return
  chart.setOption(props.option, true)
}

onMounted(() => {
  chart = echarts.init(chartRef.value)
  render()
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(chartRef.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer && observer.disconnect()
  chart && chart.dispose()
  chart = null
})
</script>

<style scoped>
.chart {
  width: 100%;
}
</style>

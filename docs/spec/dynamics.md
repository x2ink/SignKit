> 历史方案：动态曲线、宽度映射与起笔造型已由 [当前参考实现规格](reference-brush.md) 替代。本文保留方案记录，不作为现版本算法契约。

# 压力与动态笔锋

- `penMode`：`FIXED`（默认兼容原行为）、`VELOCITY`、`PRESSURE`、`PRESSURE_AND_VELOCITY`。
- `strokeWidth` 在动态模式下是最大宽度（px）；`minStrokeWidthRatio` 默认 0.25，有效范围 (0,1]，最小宽度为最大宽度乘比例。
- `velocityFilterWeight` 默认 0.7，有效范围 (0,1]。速度以相邻接受样本的距离 dp / 事件时间差 ms 计算，滤波后映射到宽度；快细慢粗。
- 压力使用主指当前与历史 pressure，限制到 [0,1]，无效值退回 1。压力低通滤波，重压更粗。设备恒定上报时不伪造压力，速度仍可生效。
- 混合模式采用有下限的压力增益乘以速度因子，再映射到宽度区间，避免低压设备失去速度响应；见 [校准规格](pen-calibration.md)。宽度再低通滤波，并在曲线内部连续插值。
- 配置按笔锁定，后续修改不影响当前与历史笔画；UP 的压力不参与宽度计算，避免常见抬笔零压力导致突变，但位置/时间照常处理。
- 微小位移过滤继续生效；无位移但有效压力变化也可接受并通知正在写。首个移动前的圆点为临时预览，压力变化可更新它；开始移动后的已定墨迹不随减压消失。
- DOWN 不能提供速度，预览圆点不写入已定轮廓。首个有效位置移动按实际速度和有效压力重新建立宽度，首段起点使用相同宽度，避免起笔留下大圆头。UP 是首次移动时同样处理，但沿用抬笔前压力。完全未移动则在 UP/CANCEL 时提交圆点。
- 动态轮廓另外按累计曲线路程限制起笔宽度，处理缓慢起步后加速；见 [起笔形状规格](startup-envelope.md)。
- 压力变化不足 0.01 且位移不足 2dp 的 MOVE 被忽略；若仅压力变化，位置吸附到上一接受点以免压力变化绕过位置去抖。
- 动态轮廓沿原中点二次曲线采样圆点并以同一填充 Path 绘制，屏幕/导出共用路径；不改变固定模式中心线路径。
- 测试 App 使用模式选择器，默认压力＋速度；粗细滑动条配置最大宽度，范围 4–64dp、默认 24dp，并显示动态宽度范围。

## 验收

同轨迹低压/高压、快/慢、固定模式不变、混合影响、历史样本一致性、UP 零压不突变、配置锁定、撤销/清空/监听回归，构建与 lint。最多三轮修复验证。

## 使用

```kotlin
binding.spvTest.penMode = SignPadView.PenMode.PRESSURE_AND_VELOCITY
binding.spvTest.strokeWidth = 10f * resources.displayMetrics.density
binding.spvTest.minStrokeWidthRatio = 0.25f
binding.spvTest.velocityFilterWeight = 0.7f
```

## 宽度映射

压力用 0.5 权重低通滤波；速度按 `velocityFilterWeight` 滤波。速度因子为 `1 / (1 + (速度 / velocityReference)^2)`，默认参考速度 1.5dp/ms。压力模式因子为归一化压力，混合模式以 `0.5 + 0.5 * 压力` 作为速度因子的增益。将因子线性映射到最小/最大宽度区间，再以 0.5 权重滤波宽度；曲线段内用 smoothstep 插值。

采样使用 [MotionEvent 的事件时间、当前与历史压力](https://developer.android.com/reference/android/view/MotionEvent#getPressure(int))。设备可以上报超过 1 的压力，本库按 [0,1] 限制；未实现设备专用压力校准。

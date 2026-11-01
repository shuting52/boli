# 懒得找了 · 液态玻璃 (LandezhaoleGlass)
Android Compose app: landezhaole.pages.dev 导航内容 + AndroidLiquidGlass 2.0.1 (io.github.kyant0:backdrop:2.0.1)。
- applicationId / namespace: com.landezhaole.liquidglass ; 入口 MainActivity -> LandezhaoleApp()
- 构建: ./gradlew assembleDebug  (JDK 17+/21, Android SDK platform 37 + build-tools 37.0.0)
- 安装: adb install -r app/build/outputs/apk/debug/app-debug.apk
- reference/ = 从技能文档原样提取的全部源码（库 + 演示 + 壳），未参与构建，供对照。
- app/src/main/java/com/kyant/backdrop/catalog/ = 演示原样拷贝的 LiquidButton/Slider/Toggle/BottomTabs 组件。
- app/src/main/assets/data.json = 网站 data.json 快照（21 个分类、2618 个站点）。

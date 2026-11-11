import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.qg"
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 2023.3 是第一个全面基于 17 的稳定版本，编译产物的 API 向下兼容更稳，
        // 装到 2024/2025/2026 版 IDEA 上同样可用。
        intellijIdeaCommunity("2023.3.4")
        pluginVerifier()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

intellijPlatform {
    // 本插件没有 GUI Designer 的 .form 表单，不需要字节码插桩；
    // 关掉可以避免插桩工具在非 JetBrains Runtime 的 JDK 上报 "Packages does not exist"
    instrumentCode = false
    buildSearchableOptions = false
    pluginConfiguration {
        id.set("com.qg.fastscroll")
        name.set("Fast Scroll")
        version.set(project.version.toString())
        vendor {
            name.set("qg")
        }
        ideaVersion {
            // 233 => 2023.3；不设上限，新版 IDEA 直接可用
            sinceBuild.set("233")
            untilBuild.set(provider { null })
        }
    }
    // ./gradlew verifyPlugin 会用目标 IDE 做一次真实加载校验
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2023.3.4")
        }
    }
}

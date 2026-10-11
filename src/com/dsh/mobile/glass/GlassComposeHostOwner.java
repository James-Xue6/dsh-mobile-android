package com.dsh.mobile.glass;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/**
 * 给**非 `ComponentActivity` 的宿主**补上 Compose 必需的三件套 ViewTree Owner。
 *
 * <h3>为什么必须有它（2026-10-08 真机 dropbox 实证）</h3>
 * {@code MainActivity} 是 {@code extends android.app.Activity}（**不是** {@code ComponentActivity}），
 * 而只有 {@code ComponentActivity.setContentView()} 会往视图树上挂
 * {@code ViewTreeLifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner}。
 * 所以在它里面直接 addView 一个 {@code ComposeView} 会当场炸：
 * <pre>
 *   java.lang.IllegalStateException: ViewTreeLifecycleOwner not found
 *     at WindowRecomposer_androidKt.createLifecycleAwareWindowRecomposer
 *     at AbstractComposeView.attachedToWindow
 * </pre>
 * （复现与栈：{@code dumpsys dropbox --print} → {@code data_app_crash}。重要：华为/荣耀 ROM 的
 * logcat 是加密的，{@code adb logcat} 读不到第三方应用的栈，**dropbox 才是入口**。）
 *
 * <h3>为什么用 Java 而不是 Kotlin 写这个文件</h3>
 * 这三个 {@code ViewTree*Owner} 是 KMP 模块的 **androidMain-only** 类
 * （缓存里能看到源码名 {@code ViewTreeLifecycleOwner.android.kt}）。Kotlin 编译器按 common
 * metadata 解析时会报 "Unresolved reference"，**javac 则能正常看到 android 变体** ——
 * 实测：同一个类路径，Kotlin 报未解析、改成 Java 一次通过。
 *
 * <h3>为什么不把 MainActivity 改成 ComponentActivity</h3>
 * 那会顺带改掉 {@code onBackPressed} 的分发语义（{@code ComponentActivity} 走
 * {@code OnBackPressedDispatcher}），对一个"全手搓、还挂着前台保活服务"的老 Activity 风险太大。
 * 这里只补三件套，并由宿主在 {@code onResume/onStop/onDestroy} 里驱动生命周期，**基类一行不动**。
 */
public final class GlassComposeHostOwner implements LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private final LifecycleRegistry registry = new LifecycleRegistry(this);
    private final ViewModelStore store = new ViewModelStore();
    private final SavedStateRegistryController controller = SavedStateRegistryController.create(this);

    public GlassComposeHostOwner() {
        // 宿主是老 Activity，没有 SavedStateRegistry 的恢复源；空 Bundle 恢复即可满足 Compose
        try {
            controller.performRestore(null);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return store;
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return controller.getSavedStateRegistry();
    }

    /** 把三件套挂到某个 View 上（挂 `ComposeView` 本身即可 —— 它 attach 时向上找的就是这个）。 */
    public void install(View v) {
        ViewTreeLifecycleOwner.set(v, this);
        ViewTreeViewModelStoreOwner.set(v, this);
        ViewTreeSavedStateRegistryOwner.set(v, this);
        resume();
    }

    public void resume() {
        try {
            registry.setCurrentState(Lifecycle.State.RESUMED);
        } catch (Throwable ignored) {
        }
    }

    public void pause() {
        try {
            registry.setCurrentState(Lifecycle.State.CREATED);
        } catch (Throwable ignored) {
        }
    }

    public void destroy() {
        try {
            registry.setCurrentState(Lifecycle.State.DESTROYED);
        } catch (Throwable ignored) {
        }
    }
}

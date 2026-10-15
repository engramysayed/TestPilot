package delivery.mobile.codegen;

import delivery.mobile.ir.MobileContext;

/** Emits a context switch from the durable predicate. Never writes a WebView process handle. */
public final class MobileContextEmit {
    private MobileContextEmit() {}

    public static String switchCall(MobileContext context) {
        if (context == null) {
            throw new IllegalArgumentException("SWITCH_CONTEXT requires context");
        }
        String pkg = context.packageName() == null ? "" : context.packageName();
        String url = context.webViewUrlMatcher() == null ? "" : context.webViewUrlMatcher();
        String title = context.webViewTitleMatcher() == null ? "" : context.webViewTitleMatcher();
        if ((pkg + url + title).contains("WEBVIEW_")) {
            throw new IllegalArgumentException("volatile WEBVIEW_ handle must not be emitted");
        }
        return "project.runtime.evidence.ContextSwitch.to(getAndroidDriver(), "
                + MobileCodegenNaming.javaStringLiteral(context.kind()) + ", "
                + MobileCodegenNaming.javaStringLiteral(pkg) + ", "
                + MobileCodegenNaming.javaStringLiteral(url) + ", "
                + MobileCodegenNaming.javaStringLiteral(title) + ")";
    }
}

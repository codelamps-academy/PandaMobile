package org.wordpress.android.util

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

open class BaseWebViewClient(
    private val baseWebViewClientListener: BaseWebViewClientListener
) : WebViewClient() {
    private var errorReceived = false
    private var requestedUrl: String? = null

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        errorReceived = false
        requestedUrl = url
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        if (!errorReceived) {
            baseWebViewClientListener.onPageLoaded()
        }
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        // From the documentation:
        // > These errors usually indicate inability to connect to the server.
        // > will be called for any resource (iframe, image, etc.), not just for the main page.
        // > Thus, it is recommended to perform minimum required work in this callback.
        if (request?.isForMainFrame == true && requestedUrl == request.url.toString()) {
            errorReceived = true
            baseWebViewClientListener.onError()
        }
    }

    interface BaseWebViewClientListener {
        fun onPageLoaded()
        fun onError()
    }
}

package com.appboy.ui.inappmessage.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.appboy.ui.inappmessage.AppboyInAppMessageManager;
import com.appboy.ui.inappmessage.IInAppMessageView;
import com.appboy.ui.inappmessage.InAppMessageWebViewClient;
import com.appboy.ui.inappmessage.listeners.IWebViewClientStateListener;

public abstract class AppboyInAppMessageHtmlBaseView extends RelativeLayout implements IInAppMessageView {
  private static final String HTML_MIME_TYPE = "text/html";
  private static final String HTML_ENCODING = "utf-8";
  private static final String FILE_URI_SCHEME_PREFIX = "file://";

  private InAppMessageWebViewClient mInAppMessageWebViewClient;

  public AppboyInAppMessageHtmlBaseView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public View getMessageClickableView() {
    return this;
  }

  /**
   * Loads the WebView using an html string and local file resource url. This url should be a path
   * to a file on the local filesystem.
   *
   * @param htmlBody   Html text encoded in utf-8
   * @param assetDirectoryUrl path to the local assets file
   */
  public void setWebViewContent(String htmlBody, String assetDirectoryUrl) {
    getMessageWebView().loadDataWithBaseURL(FILE_URI_SCHEME_PREFIX + assetDirectoryUrl + "/", htmlBody, HTML_MIME_TYPE, HTML_ENCODING, null);
  }

  public void setInAppMessageWebViewClient(InAppMessageWebViewClient inAppMessageWebViewClient) {
    getMessageWebView().setWebViewClient(inAppMessageWebViewClient);
    mInAppMessageWebViewClient = inAppMessageWebViewClient;
  }

  public void setHtmlPageFinishedListener(IWebViewClientStateListener listener) {
    if (mInAppMessageWebViewClient != null) {
      mInAppMessageWebViewClient.setWebViewClientStateListener(listener);
    }
  }

  /**
   * @return The {@link WebView} displaying the HTML content of this in-app message.
   */
  public abstract WebView getMessageWebView();

  /**
   * Html in-app messages can alternatively be closed by the back button.
   *
   * Note: If the internal WebView has focus instead of this view, back button events on html
   * in-app messages are handled separately in {@link AppboyInAppMessageWebView#onKeyDown(int, KeyEvent)}
   *
   * @return If the button pressed was the back button, close the in-app message
   * and return true to indicate that the event was handled.
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && AppboyInAppMessageManager.getInstance().getDoesBackButtonDismissInAppMessageView()) {
      InAppMessageViewUtils.closeInAppMessageOnKeycodeBack();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  /**
   * HTML messages can alternatively be closed by the back button.
   *
   * @return If the button pressed was the back button, close the in-app message
   * and return true to indicate that the event was handled.
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (!isInTouchMode() && event.getKeyCode() == KeyEvent.KEYCODE_BACK && AppboyInAppMessageManager.getInstance().getDoesBackButtonDismissInAppMessageView()) {
      InAppMessageViewUtils.closeInAppMessageOnKeycodeBack();
      return true;
    }
    return super.dispatchKeyEvent(event);
  }
}

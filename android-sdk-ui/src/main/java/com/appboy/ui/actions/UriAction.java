package com.appboy.ui.actions;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.appboy.Constants;
import com.appboy.configuration.AppboyConfigurationProvider;
import com.appboy.enums.Channel;
import com.appboy.support.AppboyFileUtils;
import com.appboy.support.AppboyLogger;
import com.appboy.support.StringUtils;
import com.appboy.ui.AppboyWebViewActivity;
import com.appboy.ui.support.UriUtils;

import java.util.List;

public class UriAction implements IAction {
  private static final String TAG = AppboyLogger.getAppboyLogTag(UriAction.class);

  private final Bundle mExtras;
  private final Channel mChannel;
  private Uri mUri;
  private boolean mUseWebView;

  /**
   * @param uri The Uri.
   * @param extras Any extras to be passed in the start intent.
   * @param useWebView If this Uri should use the Webview, if the Uri is a remote Uri
   * @param channel The channel for the Uri. Must not be null.
   */
  UriAction(@NonNull Uri uri, Bundle extras, boolean useWebView, @NonNull Channel channel) {
    mUri = uri;
    mExtras = extras;
    mUseWebView = useWebView;
    mChannel = channel;
  }

  /**
   * Constructor to copy an existing {@link UriAction}.
   *
   * @param originalUriAction A {@link UriAction} to copy parameters from.
   */
  public UriAction(@NonNull UriAction originalUriAction) {
    this.mUri = originalUriAction.mUri;
    this.mExtras = originalUriAction.mExtras;
    this.mUseWebView = originalUriAction.mUseWebView;
    this.mChannel = originalUriAction.mChannel;
  }

  @Override
  public Channel getChannel() {
    return mChannel;
  }

  /**
   * Opens the action's Uri properly based on mUseWebView status and channel.
   */
  @Override
  public void execute(Context context) {
    if (AppboyFileUtils.isLocalUri(mUri)) {
      AppboyLogger.d(TAG, "Not executing local Uri: " + mUri);
      return;
    }
    AppboyLogger.d(TAG, "Executing Uri action from channel " + mChannel + ": " + mUri + ". UseWebView: " + mUseWebView + ". Extras: " + mExtras);
    if (mUseWebView && AppboyFileUtils.REMOTE_SCHEMES.contains(mUri.getScheme())) {
      // If the scheme is not a remote scheme, we open it using an ACTION_VIEW intent.
      if (mChannel.equals(Channel.PUSH)) {
        openUriWithWebViewActivityFromPush(context, mUri, mExtras);
      } else {
        openUriWithWebViewActivity(context, mUri, mExtras);
      }
    } else {
      if (mChannel.equals(Channel.PUSH)) {
        openUriWithActionViewFromPush(context, mUri, mExtras);
      } else {
        openUriWithActionView(context, mUri, mExtras);
      }
    }
  }

  public void setUri(@NonNull Uri uri) {
    mUri = uri;
  }

  public void setUseWebView(boolean openInWebView) {
    mUseWebView = openInWebView;
  }

  /**
   * @return the {@link Uri} that represents this {@link UriAction}.
   */
  @NonNull
  public Uri getUri() {
    return mUri;
  }

  /**
   * @return whether this {@link UriAction} should open
   */
  public boolean getUseWebView() {
    return mUseWebView;
  }

  public Bundle getExtras() {
    return mExtras;
  }

  /**
   * Opens the remote scheme Uri in {@link AppboyWebViewActivity}.
   */
  protected void openUriWithWebViewActivity(Context context, Uri uri, Bundle extras) {
    Intent intent = getWebViewActivityIntent(context, uri, extras);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    try {
      context.startActivity(intent);
    } catch (Exception e) {
      AppboyLogger.e(TAG, "Appboy AppboyWebViewActivity not opened successfully.", e);
    }
  }

  /**
   * Uses an Intent.ACTION_VIEW intent to open the Uri.
   */
  protected void openUriWithActionView(Context context, Uri uri, Bundle extras) {
    Intent intent = getActionViewIntent(context, uri, extras);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    if (intent.resolveActivity(context.getPackageManager()) != null) {
      context.startActivity(intent);
    } else {
      AppboyLogger.w(TAG, "Could not find appropriate activity to open for deep link " + uri + ".");
    }
  }

  /**
   * Opens the remote scheme Uri in {@link AppboyWebViewActivity} while also populating the back stack.
   *
   * @see UriAction#getIntentArrayWithConfiguredBackStack(Context, Bundle, Intent)
   */
  protected void openUriWithWebViewActivityFromPush(Context context, Uri uri, Bundle extras) {
    AppboyConfigurationProvider configurationProvider = new AppboyConfigurationProvider(context);
    try {
      Intent webViewIntent = getWebViewActivityIntent(context, uri, extras);
      context.startActivities(getIntentArrayWithConfiguredBackStack(context, extras, webViewIntent, configurationProvider));
    } catch (Exception e) {
      AppboyLogger.e(TAG, "Braze WebView Activity not opened successfully.", e);
    }
  }

  /**
   * Uses an {@link Intent#ACTION_VIEW} intent to open the {@link Uri} and places the main activity of the
   * activity on the back stack.
   *
   * @see UriAction#getIntentArrayWithConfiguredBackStack(Context, Bundle, Intent)
   */
  protected void openUriWithActionViewFromPush(Context context, Uri uri, Bundle extras) {
    AppboyConfigurationProvider configurationProvider = new AppboyConfigurationProvider(context);
    try {
      Intent uriIntent = getActionViewIntent(context, uri, extras);
      context.startActivities(getIntentArrayWithConfiguredBackStack(context, extras, uriIntent, configurationProvider));
    } catch (ActivityNotFoundException e) {
      AppboyLogger.w(TAG, "Could not find appropriate activity to open for deep link " + uri, e);
    }
  }

  /**
   * Returns an intent that opens the uri inside of a {@link AppboyWebViewActivity}.
   */
  protected Intent getWebViewActivityIntent(Context context, Uri uri, Bundle extras) {
    AppboyConfigurationProvider configurationProvider = new AppboyConfigurationProvider(context);
    final String customWebViewActivityClassName = configurationProvider.getCustomHtmlWebViewActivityClassName();
    Intent webViewActivityIntent;

    // If the class is valid and is manifest registered, use it as the launching intent
    if (!StringUtils.isNullOrBlank(customWebViewActivityClassName)
        && UriUtils.isActivityRegisteredInManifest(context, customWebViewActivityClassName)) {
      AppboyLogger.d(TAG, "Launching custom WebView Activity with class name: " + customWebViewActivityClassName);
      webViewActivityIntent = new Intent()
          .setClassName(context, customWebViewActivityClassName);
    } else {
      webViewActivityIntent = new Intent(context, AppboyWebViewActivity.class);
    }

    if (extras != null) {
      webViewActivityIntent.putExtras(extras);
    }
    webViewActivityIntent.putExtra(Constants.APPBOY_WEBVIEW_URL_EXTRA, uri.toString());
    return webViewActivityIntent;
  }

  protected Intent getActionViewIntent(Context context, Uri uri, Bundle extras) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(uri);

    if (extras != null) {
      intent.putExtras(extras);
    }

    // If the current app can already handle the intent, default to using it
    List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, 0);
    if (resolveInfos.size() > 1) {
      for (ResolveInfo resolveInfo : resolveInfos) {
        if (resolveInfo.activityInfo.packageName.equals(context.getPackageName())) {
          AppboyLogger.d(TAG, "Setting deep link activity to " + resolveInfo.activityInfo.packageName + ".");
          intent.setPackage(resolveInfo.activityInfo.packageName);
          break;
        }
      }
    }

    return intent;
  }

  /**
   * Gets an {@link Intent} array that has the configured back stack functionality.
   *
   * @param targetIntent The ultimate intent to be followed. For example, the main/launcher intent would be the penultimate {@link Intent}.
   *
   * @see AppboyConfigurationProvider#getIsPushDeepLinkBackStackActivityEnabled()
   * @see AppboyConfigurationProvider#getPushDeepLinkBackStackActivityClassName()
   */
  @VisibleForTesting
  protected Intent[] getIntentArrayWithConfiguredBackStack(Context context,
                                                           Bundle extras,
                                                           Intent targetIntent,
                                                           AppboyConfigurationProvider configurationProvider) {
    // The root intent will either point to the launcher activity,
    // some custom activity, or nothing if the back-stack is disabled.
    Intent rootIntent = null;

    if (configurationProvider.getIsPushDeepLinkBackStackActivityEnabled()) {
      // If a custom back stack class is defined, then set it
      final String pushDeepLinkBackStackActivityClassName = configurationProvider.getPushDeepLinkBackStackActivityClassName();
      if (StringUtils.isNullOrBlank(pushDeepLinkBackStackActivityClassName)) {
        AppboyLogger.i(TAG, "Adding main activity intent to back stack while opening uri from push");
        rootIntent = UriUtils.getMainActivityIntent(context, extras);
      } else {
        // Check if the activity is registered in the manifest. If not, then add nothing to the back stack
        if (UriUtils.isActivityRegisteredInManifest(context, pushDeepLinkBackStackActivityClassName)) {
          AppboyLogger.i(TAG, "Adding custom back stack activity while opening uri from push: " + pushDeepLinkBackStackActivityClassName);
          rootIntent = new Intent()
              .setClassName(context, pushDeepLinkBackStackActivityClassName)
              .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              .putExtras(extras);
        } else {
          AppboyLogger.i(TAG, "Not adding unregistered activity to the back stack while opening uri from push: " + pushDeepLinkBackStackActivityClassName);
        }
      }
    } else {
      AppboyLogger.i(TAG, "Not adding back stack activity while opening uri from push due to disabled configuration setting.");
    }

    if (rootIntent == null) {
      // Calling startActivities() from outside of an Activity
      // context requires the FLAG_ACTIVITY_NEW_TASK flag on the first Intent
      targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      // Just return the target intent by itself
      return new Intent[]{targetIntent};
    } else {
      // Return the intents in their stack order
      return new Intent[]{rootIntent, targetIntent};
    }
  }
}

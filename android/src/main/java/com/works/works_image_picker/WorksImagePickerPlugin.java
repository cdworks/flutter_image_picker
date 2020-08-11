package com.works.works_image_picker;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** WorksImagePickerPlugin */
public class WorksImagePickerPlugin implements FlutterPlugin, MethodCallHandler {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  static final String METHOD_CALL_IMAGE = "pickImage";
  static final String METHOD_CALL_VIDEO = "pickVideo";
  private static final String METHOD_CALL_RETRIEVE = "retrieve";

  private static final String CHANNEL = "works_image_picker";

  private static final int SOURCE_CAMERA = 0;
  private static final int SOURCE_GALLERY = 1;

  private final PluginRegistry.Registrar registrar;
  private ImagePickerDelegate delegate;
  private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
  private MethodChannel channel;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    FileUtils.applicationContext = registrar.context();
    final ImagePickerCache cache = new ImagePickerCache(registrar.activity());
    channel = new MethodChannel(registrar.messenger(), CHANNEL);

    final File externalFilesDirectory =
            registrar.activity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    final ExifDataCopier exifDataCopier = new ExifDataCopier();
    final ImageResizer imageResizer = new ImageResizer(externalFilesDirectory, exifDataCopier);
    final ImagePickerDelegate delegate =
            new ImagePickerDelegate(registrar.activity(), externalFilesDirectory, imageResizer, cache);
    registrar.addActivityResultListener(delegate);
    registrar.addRequestPermissionsResultListener(delegate);
    final WorksImagePickerPlugin instance = new WorksImagePickerPlugin(registrar, delegate);

    channel.setMethodCallHandler(instance);
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  public static void registerWith(Registrar registrar) {
    if (registrar.activity() == null) {
      // If a background flutter view tries to register the plugin, there will be no activity from the registrar,
      // we stop the registering process immediately because the ImagePicker requires an activity.
      return;
    }
    FileUtils.applicationContext = registrar.context();
    final ImagePickerCache cache = new ImagePickerCache(registrar.activity());
    final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);

    final File externalFilesDirectory =
            registrar.activity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    final ExifDataCopier exifDataCopier = new ExifDataCopier();
    final ImageResizer imageResizer = new ImageResizer(externalFilesDirectory, exifDataCopier);
    final ImagePickerDelegate delegate =
            new ImagePickerDelegate(registrar.activity(), externalFilesDirectory, imageResizer, cache);
    registrar.addActivityResultListener(delegate);
    registrar.addRequestPermissionsResultListener(delegate);
    final WorksImagePickerPlugin instance = new WorksImagePickerPlugin(registrar, delegate);

    channel.setMethodCallHandler(instance);
  }

  @VisibleForTesting
  WorksImagePickerPlugin(final PluginRegistry.Registrar registrar, final ImagePickerDelegate delegate) {
    this.registrar = registrar;
    this.delegate = delegate;
    this.activityLifecycleCallbacks =
            new Application.ActivityLifecycleCallbacks() {
              @Override
              public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

              @Override
              public void onActivityStarted(Activity activity) {}

              @Override
              public void onActivityResumed(Activity activity) {}

              @Override
              public void onActivityPaused(Activity activity) {}

              @Override
              public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                if (activity == registrar.activity()) {
                  delegate.saveStateBeforeResult();
                }
              }

              @Override
              public void onActivityDestroyed(Activity activity) {
                if (activity == registrar.activity()
                        && registrar.activity().getApplicationContext() != null) {
                  ((Application) registrar.activity().getApplicationContext())
                          .unregisterActivityLifecycleCallbacks(
                                  this); // Use getApplicationContext() to avoid casting failures
                }
              }

              @Override
              public void onActivityStopped(Activity activity) {}
            };

    if (this.registrar != null
            && this.registrar.context() != null
            && this.registrar.context().getApplicationContext() != null) {
      ((Application) this.registrar.context().getApplicationContext())
              .registerActivityLifecycleCallbacks(
                      this
                              .activityLifecycleCallbacks); // Use getApplicationContext() to avoid casting failures.
    }
  }

  // MethodChannel.Result wrapper that responds on the platform thread.
  private static class MethodResultWrapper implements MethodChannel.Result {
    private MethodChannel.Result methodResult;
    private Handler handler;

    MethodResultWrapper(MethodChannel.Result result) {
      methodResult = result;
      handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void success(final Object result) {
      handler.post(
              new Runnable() {
                @Override
                public void run() {
                  methodResult.success(result);
                }
              });
    }

    @Override
    public void error(
            final String errorCode, final String errorMessage, final Object errorDetails) {
      handler.post(
              new Runnable() {
                @Override
                public void run() {
                  methodResult.error(errorCode, errorMessage, errorDetails);
                }
              });
    }

    @Override
    public void notImplemented() {
      handler.post(
              new Runnable() {
                @Override
                public void run() {
                  methodResult.notImplemented();
                }
              });
    }
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result rawResult) {
    if (registrar.activity() == null) {
      rawResult.error("no_activity", "image_picker plugin requires a foreground activity.", null);
      return;
    }
    MethodChannel.Result result = new MethodResultWrapper(rawResult);
    int imageSource;
    switch (call.method) {
      case METHOD_CALL_IMAGE:
        imageSource = call.argument("source");
        switch (imageSource) {
          case SOURCE_GALLERY:
            delegate.chooseImageFromGallery(call, result);
            break;
          case SOURCE_CAMERA:
            delegate.takeImageWithCamera(call, result);
            break;
          default:
            throw new IllegalArgumentException("Invalid image source: " + imageSource);
        }
        break;
      case METHOD_CALL_VIDEO:
        imageSource = call.argument("source");
        switch (imageSource) {
          case SOURCE_GALLERY:
            delegate.chooseVideoFromGallery(call, result);
            break;
          case SOURCE_CAMERA:
            delegate.takeVideoWithCamera(call, result);
            break;
          default:
            throw new IllegalArgumentException("Invalid video source: " + imageSource);
        }
        break;
      case METHOD_CALL_RETRIEVE:
        delegate.retrieveLostImage(result);
        break;
      default:
        throw new IllegalArgumentException("Unknown method " + call.method);
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
}

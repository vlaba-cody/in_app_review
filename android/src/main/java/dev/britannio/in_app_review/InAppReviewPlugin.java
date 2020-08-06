package dev.britannio.in_app_review;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.tasks.OnCompleteListener;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.Task;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/** InAppReviewPlugin */
public class InAppReviewPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;

  private Context context;
  private Activity activity;

  private ReviewInfo reviewInfo;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "dev.britannio.in_app_review");
    channel.setMethodCallHandler(this);
    context = flutterPluginBinding.getApplicationContext();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("isAvailable")) {
      isAvailable(result);
    } else if (call.method.equals("requestReview")) {
      requestReview(result);
    } else {
      result.notImplemented();
    }
  }

  private void isAvailable(final Result result) {
    final boolean playStoreInstalled = isPlayStoreInstalled();
    final boolean lollipopOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    if (!(playStoreInstalled && lollipopOrLater)) {
      // The play store isn't installed or the device isn't running Android Lollipop(SDK 21) or
      // higher
      result.success(false);
    } else {
      // The API is likely available but we can ensure that it is by getting a ReviewInfo object
      // from the API. This will also speed up the review flow when we're ready to launch it as
      // the ReviewInfo doesn't need to be fetched.
      cacheReviewInfo(result);
    }
  }

  private void cacheReviewInfo(final Result result) {
    if (context == null) {
      result.error("error", "Android context not available", null);
      return;
    }
    final ReviewManager manager = ReviewManagerFactory.create(context);

    final Task<ReviewInfo> request = manager.requestReviewFlow();

    request.addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
      @Override
      public void onComplete(@NonNull Task<ReviewInfo> task) {
        if (task.isSuccessful()) {
          // We can get the ReviewInfo object
          reviewInfo =  task.getResult();
          result.success(true);
        } else {
          // The API isn't available
          result.success(false);
        }
      }
    });
  }

  private void requestReview(final Result result) {
    if (context == null) {
      result.error("error", "Android context not available", null);
      return;
    }
    if (activity == null) {
      result.error("error","Android activity not available", null);
    }

    final ReviewManager manager = ReviewManagerFactory.create(context);


    if (reviewInfo != null) {
      launchReviewFlow(result, manager, reviewInfo);
      return;
    }

    final Task<ReviewInfo> request = manager.requestReviewFlow();

    request.addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
      @Override
      public void onComplete(@NonNull Task<ReviewInfo> task) {
        if (task.isSuccessful()) {
          // We can get the ReviewInfo object
          ReviewInfo reviewInfo = task.getResult();
          launchReviewFlow(result, manager, reviewInfo);
        } else {
          result.error("error","In-App Review API unavailable", null);
        }
      }
    });
  }

  private void launchReviewFlow(final Result result, ReviewManager manager, ReviewInfo reviewInfo) {
    Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
    flow.addOnCompleteListener(new OnCompleteListener<Void>() {
      @Override
      public void onComplete(@NonNull Task<Void> task) {
        result.success(null);
      }
    });
  }

  private boolean isPlayStoreInstalled(){
    try {
      context.getPackageManager().getPackageInfo("com.android.vending", 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    context = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }
}

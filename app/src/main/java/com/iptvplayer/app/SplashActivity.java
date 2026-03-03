package com.iptvplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.iv_splash_logo);

        // Set alpha 0 via Java — lebih reliable daripada android:alpha di XML
        logo.setAlpha(0f);

        // Delay sedikit agar layout selesai inflate sebelum animasi mulai
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(900);
            fadeIn.setFillAfter(true);

            fadeIn.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationRepeat(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    logo.setAlpha(1f); // pastikan visible setelah fade in
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                        fadeOut.setDuration(700);
                        fadeOut.setFillAfter(true);
                        fadeOut.setAnimationListener(new Animation.AnimationListener() {
                            @Override public void onAnimationStart(Animation a) {}
                            @Override public void onAnimationRepeat(Animation a) {}
                            @Override public void onAnimationEnd(Animation a) {
                                goToMain();
                            }
                        });
                        logo.startAnimation(fadeOut);
                    }, 700);
                }
            });

            logo.startAnimation(fadeIn);
        }, 100);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}

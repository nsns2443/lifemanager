package com.azharul.lifemanager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    /** আপনার Apps Script অ্যাপের /exec ঠিকানা এখানে বসাতে পারেন। ফাঁকা বা না বসালে প্রথমবার অ্যাপ নিজেই জিজ্ঞেস করবে। */
    static final String DEFAULT_URL = "";

    private static final int REQ_FILE = 11;
    private static final int REQ_NOTIF = 12;

    static volatile String pendingGo = "";
    private WebView printWeb;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private boolean errorShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#2563EB"));
        TaskWidget.ensureFont(this);
        readGo(getIntent());

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // রিমাইন্ডারের শব্দ ছোঁয়া ছাড়াই বাজবে
        s.setAllowFileAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                String host = u.getHost() == null ? "" : u.getHost();
                boolean isWeb = scheme.equals("http") || scheme.equals("https");
                boolean google = host.endsWith("google.com") || host.endsWith("googleusercontent.com")
                        || host.endsWith("blogspot.com") || host.endsWith("blogger.com");
                String mine = "";
                try {
                    String su = getSavedUrl();
                    mine = su.isEmpty() ? "" : (Uri.parse(su).getHost() == null ? "" : Uri.parse(su).getHost());
                } catch (Exception ignored) {
                }
                if (isWeb && host.endsWith("blogspot.com")) {
                    String fixed = fixBlog(u.toString());
                    if (!fixed.equals(u.toString())) {
                        view.loadUrl(fixed);
                        return true;
                    }
                }
                if (isWeb && (google || (!mine.isEmpty() && host.equals(mine)))) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !errorShown) {
                    errorShown = true;
                    showErrorDialog();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                errorShown = false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        askNotificationPermission();

        String url = getSavedUrl();
        if (url.isEmpty()) {
            showUrlDialog("আপনার অ্যাপের ঠিকানা (…/exec) পেস্ট করুন");
        } else {
            web.loadUrl(fixBlog(url));
        }
    }

    private SharedPreferences cfg() {
        return getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    /** Blogspot-এর মোবাইল সংস্করণ (?m=1) বন্ধ করে ডেস্কটপ সংস্করণ (?m=0) খোলায় */
    static String fixBlog(String url) {
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost() == null ? "" : u.getHost();
            if (!h.endsWith("blogspot.com")) return url;
            if ("0".equals(u.getQueryParameter("m"))) return url;
            Uri.Builder b = u.buildUpon().clearQuery();
            for (String n : u.getQueryParameterNames()) {
                if (!n.equals("m")) b.appendQueryParameter(n, u.getQueryParameter(n));
            }
            b.appendQueryParameter("m", "0");
            return b.build().toString();
        } catch (Exception e) {
            return url;
        }
    }

    private String getSavedUrl() {
        String u = cfg().getString("url", "");
        if (u == null || u.isEmpty()) u = DEFAULT_URL;
        return u == null ? "" : u.trim();
    }

    private void showUrlDialog(String msg) {
        final EditText et = new EditText(this);
        et.setHint("https://script.google.com/macros/s/.../exec");
        et.setSingleLine(false);
        et.setText(getSavedUrl());
        LinearLayout box = new LinearLayout(this);
        box.setPadding(48, 24, 48, 0);
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("অ্যাপের ঠিকানা")
                .setMessage(msg)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("চালু করুন", (d, w) -> {
                    String u = et.getText().toString().trim();
                    if (!u.startsWith("http")) {
                        Toast.makeText(this, "ঠিকানা https:// দিয়ে শুরু হতে হবে", Toast.LENGTH_LONG).show();
                        showUrlDialog(msg);
                        return;
                    }
                    cfg().edit().putString("url", u).apply();
                    web.loadUrl(fixBlog(u));
                })
                .show();
    }

    private void showErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("পেজ খোলা যায়নি")
                .setMessage("ইন্টারনেট চালু আছে কিনা দেখুন, অথবা ঠিকানা ভুল হতে পারে।")
                .setPositiveButton("আবার চেষ্টা", (d, w) -> {
                    errorShown = false;
                    String u = getSavedUrl();
                    if (!u.isEmpty()) web.loadUrl(fixBlog(u));
                })
                .setNeutralButton("ঠিকানা বদলান", (d, w) -> showUrlDialog("সঠিক ঠিকানা (…/exec) পেস্ট করুন"))
                .setNegativeButton("বন্ধ", (d, w) -> errorShown = false)
                .show();
    }

    private void askNotificationPermission() {
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_NOTIF);
    }

    /** ফোনের সর্বশেষ জানা অবস্থান নিয়ে নামাজের সময়ের জন্য রাখে */
    private void updateLocation() {
        TaskWidget.refreshLocation(this);
        TaskWidget.refreshAll(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        updateLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLocation();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readGo(intent);
    }

    private void readGo(Intent intent) {
        try {
            String g = intent == null ? null : intent.getStringExtra("go");
            if (g != null && !g.isEmpty()) {
                pendingGo = g;
                intent.removeExtra("go");
            }
        } catch (Throwable ignored) {
        }
    }

    /** রিপোর্টের HTML থেকে PDF/প্রিন্ট (ফোনের "Save as PDF" দিয়ে) */
    private void printHtml(final String html, final String title) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    printWeb = new WebView(MainActivity.this);
                    printWeb.getSettings().setJavaScriptEnabled(false);
                    printWeb.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            try {
                                android.print.PrintManager pm = (android.print.PrintManager) getSystemService(Context.PRINT_SERVICE);
                                String name = (title == null || title.isEmpty()) ? "রিপোর্ট" : title;
                                pm.print(name, view.createPrintDocumentAdapter(name), new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).setMinMargins(new android.print.PrintAttributes.Margins(450, 450, 450, 450)).setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR).build());
                            } catch (Throwable t) {
                                Toast.makeText(MainActivity.this, "প্রিন্ট চালু করা যায়নি", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    printWeb.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null);
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "প্রিন্ট চালু করা যায়নি", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    /** ওয়েব-অ্যাপ (JavaScript) থেকে ডাকা হয় */
    public class Bridge {
        @JavascriptInterface
        public boolean isNative() {
            return true;
        }

        @JavascriptInterface
        public void setReminders(String json) {
            Reminders.save(MainActivity.this, json);
            Reminders.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public void setWidget(String json) {
            TaskWidget.save(MainActivity.this, json);
            TaskWidget.refreshAll(MainActivity.this);
        }

        @JavascriptInterface
        public String takeAction() {
            String g = pendingGo;
            pendingGo = "";
            return g == null ? "" : g;
        }

        @JavascriptInterface
        public void printHtml(String html, String title) {
            MainActivity.this.printHtml(html, title);
        }

        @JavascriptInterface
        public void clearReminders() {
            Reminders.cancelAll(MainActivity.this);
        }
    }
}

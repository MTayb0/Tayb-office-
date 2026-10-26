package com.tayb.office;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingName = null;
    private String pendingData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("window.checkPending && window.checkPending()", null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Choose a file"), 1);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        handleIntent(getIntent());
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        web.evaluateJavascript("window.checkPending && window.checkPending()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /** Reads a file sent from WhatsApp / Files / Share and keeps it for the web page. */
    private void handleIntent(Intent it) {
        if (it == null) return;
        Uri u = null;
        String action = it.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            u = it.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            u = it.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (u == null) return;
        try {
            String name = displayName(u);
            if (name.indexOf('.') < 0) {
                String type = getContentResolver().getType(u);
                if (type != null) {
                    if (type.contains("spreadsheetml")) name += ".xlsx";
                    else if (type.contains("wordprocessingml")) name += ".docx";
                    else if (type.contains("pdf")) name += ".pdf";
                    else if (type.contains("csv")) name += ".csv";
                    else name += ".txt";
                }
            }
            InputStream in = getContentResolver().openInputStream(u);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            pendingName = name;
            pendingData = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            Toast.makeText(this, "Could not read the file", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri u) {
        String name = null;
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) { }
        if (name == null || name.isEmpty()) {
            name = u.getLastPathSegment();
            if (name == null) name = "file";
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
        }
        return name;
    }

    /** Functions the web page can call. */
    private class Bridge {
        @JavascriptInterface
        public String pendingName() { return pendingName == null ? "" : pendingName; }

        @JavascriptInterface
        public String pendingData() { return pendingData == null ? "" : pendingData; }

        @JavascriptInterface
        public void clearPending() { pendingName = null; pendingData = null; }

        @JavascriptInterface
        public void saveFile(String name, String mime, String base64) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TaybOffice");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                OutputStream o = getContentResolver().openOutputStream(uri);
                o.write(Base64.decode(base64, Base64.DEFAULT));
                o.close();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Saved in Downloads/TaybOffice: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Could not save the file", Toast.LENGTH_LONG).show());
            }
        }
    }
}

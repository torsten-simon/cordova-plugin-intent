package com.napolitano.cordova.plugin.intent;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaInterfaceImpl;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64OutputStream;
import android.util.Log;
import android.net.Uri;

import android.content.ContentResolver;
import android.content.Context;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class IntentPlugin extends CordovaPlugin {

    private final String pluginName = "IntentPlugin";
    private CallbackContext onNewIntentCallbackContext = null;

    /**
     * Generic plugin command executor
     *
     * @param action
     * @param data
     * @param callbackContext
     * @return
     */
    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        Log.d(pluginName, pluginName + " called with options: " + data);

        Class params[] = new Class[2];
        params[0] = JSONArray.class;
        params[1] = CallbackContext.class;

        try {
            Method method = this.getClass().getDeclaredMethod(action, params);
            method.invoke(this, data, callbackContext);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return true;
    }

    /**
     * Send a JSON representation of the cordova intent back to the caller
     *
     * @param data
     * @param context
     */
    public boolean getCordovaIntent (final JSONArray data, final CallbackContext context) {
        if(data.length() != 0) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        Intent intent = cordova.getActivity().getIntent();
        context.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
        return true;
    }
    /**
     * Clears the last intent, basically means that it is consumed
     *
     */
    public boolean clearIntent (final JSONArray data, final CallbackContext context) {
      cordova.getActivity().setIntent(null);
      context.sendPluginResult(new PluginResult(PluginResult.Status.OK));
      return true;
    }

    /**
     * open an app chooser for the given file path
     *
     * @param data
     * @param context
     */
    public boolean showOpenWith(final JSONArray data, final CallbackContext context) {
        if(data.length() != 1) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
        try {
            String url=data.getString(0);
	    // prevent android.os.FileUriExposedException:
	    if(url.startsWith("file://"))
		url=url.substring(7);
            Uri uri=Uri.parse(url);
            String name=uri.getLastPathSegment();
            String extension=url.substring(url.lastIndexOf(".")+1).toLowerCase();
            String mimetype=MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if(mimetype==null) mimetype="*/*";
            Intent target=new Intent(Intent.ACTION_VIEW);
            target.setDataAndType(uri,mimetype);
            Intent intent = Intent.createChooser(target,name);
            cordova.getActivity().startActivity(intent);
            context.sendPluginResult(new PluginResult(PluginResult.Status.OK, getIntentJson(intent)));
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
    }

    /**
     * Register handler for onNewIntent event
     *
     * @param data
     * @param context
     * @return
     */
    public boolean setNewIntentHandler (final JSONArray data, final CallbackContext context) {
        if(data.length() != 1) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }

        this.onNewIntentCallbackContext = context;

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        context.sendPluginResult(result);
        return true;
    }

    /**
     * Triggered on new intent
     *
     * @param intent
     */
    @Override
    public void onNewIntent(Intent intent) {
        if (this.onNewIntentCallbackContext != null) {

            PluginResult result = new PluginResult(PluginResult.Status.OK, getIntentJson(intent));
            result.setKeepCallback(true);
            this.onNewIntentCallbackContext.sendPluginResult(result);
        }
    }

    /**
     * Return JSON representation of intent attributes
     *
     * @param intent
     * @return
     */
    @SuppressLint("Range")
    private JSONObject getIntentJson(Intent intent) {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if(clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {

                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if(item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));
                            try (Cursor c=((CordovaInterfaceImpl)cordova).
                              getActivity().getContentResolver().query(item.getUri(), null, null, null, null)
                            ) {
                              c.moveToFirst();
                              items[i].put("file", c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME)));
                            } catch (Throwable ignored) {
                            }
                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    } catch (JSONException e) {
                        Log.d(pluginName, pluginName + " Error thrown during intent > JSON conversion");
                        Log.d(pluginName, e.getMessage());
                        Log.d(pluginName, Arrays.toString(e.getStackTrace()));
                    }

                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());

            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());
            intentJSON.put("stream", toBase64(intent));

            return intentJSON;
        } catch (JSONException e) {
            Log.d(pluginName, pluginName + " Error thrown during intent > JSON conversion");
            Log.d(pluginName, e.getMessage());
            Log.d(pluginName, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }
	 private String toBase64(Intent intent){
        if (!intent.hasExtra(Intent.EXTRA_STREAM)){
            return null;
        }
        try {
            InputStream is = cordova.getActivity().getContentResolver().openInputStream((Uri)(intent.getExtras().get(Intent.EXTRA_STREAM)));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Base64OutputStream os = new Base64OutputStream(bos, Base64.DEFAULT);
            while(true){
                byte[] data=new byte[1024*1024];
                int l=is.read(data);
                if(l<=0)
                    break;
              os.write(
                data, 0, l
              );
            }
            is.close();
            os.close();
            return bos.toString();
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private static JSONObject toJsonObject(Bundle bundle) {
        try {
            return (JSONObject) toJsonValue(bundle);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot convert bundle to JSON: " + e.getMessage(), e);
        }
    }

    private static Object toJsonValue(final Object value) throws JSONException {
        if (value == null) {
            return null;
        } else if (value instanceof Bundle) {
            final Bundle bundle = (Bundle) value;
            final JSONObject result = new JSONObject();
            for (final String key : bundle.keySet()) {
                result.put(key, toJsonValue(bundle.get(key)));
            }
            return result;
        } else if (value.getClass().isArray()) {
            final JSONArray result = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                result.put(i, toJsonValue(Array.get(value, i)));
            }
            return result;
        } else if (
                value instanceof String
                        || value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof Long
                        || value instanceof Double) {
            return value;
        } else {
            return String.valueOf(value);
        }
    }

    public boolean getRealPathFromContentUrl(final JSONArray data, final CallbackContext context) {
        if(data.length() != 1) {
            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = cR.query(Uri.parse(data.getString(0)),  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();

            context.sendPluginResult(new PluginResult(PluginResult.Status.OK, cursor.getString(column_index)));
            return true;
        } finally {
            if (cursor != null) {
                cursor.close();
            }

            context.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
    }
}

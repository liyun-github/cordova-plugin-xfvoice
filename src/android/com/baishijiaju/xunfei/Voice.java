package com.baishijiaju.xunfei;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Environment;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This class echoes a string called from JavaScript.
 */
public class Voice extends CordovaPlugin {

    public static final String APPID_PROPERTY_KEY = "appid";
    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final String AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final int REQ_CODE = 0;
    protected CallbackContext _callbackContext;

    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音听写UI
    private RecognizerDialog mIatDialog;
    private boolean initStatus = false;
    protected String appId;

    /**
     * Constructor.
     */
    public Voice() {
    }

    /**
    * Sets the context of the Command. This can then be used to do things like
    * get file paths associated with the Activity.
    *
    * @param cordova The context of the main Activity.
    * @param webView The CordovaWebView Cordova is running in.
    */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // 应用程序入口处调用，避免手机内存过小，杀死后台进程后通过历史intent进入Activity造成SpeechUtility对象为null
        // 如在Application中调用初始化，需要在Mainifest中注册该Applicaiton
        // 注意：此接口在非主进程调用会返回null对象，如需在非主进程使用语音功能，请增加参数：SpeechConstant.FORCE_LOGIN+"=true"
        // 参数间使用半角“,”分隔。
        // 设置你申请的应用appid,请勿在'='与appid之间添加空格及空转义符

        // 注意： appid 必须和下载的SDK保持一致，否则会出现10407错误
        this.appId = preferences.getString(APPID_PROPERTY_KEY, "");
        SpeechUtility.createUtility(cordova.getActivity(),
                "appid=" + this.appId + "," + SpeechConstant.FORCE_LOGIN + "=true");

        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        mIat = SpeechRecognizer.createRecognizer(cordova.getActivity(), mInitListener);
        //创建 RecognizerDialog 对象
        mIatDialog = new RecognizerDialog(cordova.getActivity(), mInitListener);
        setParam();
    }


    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return                  True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        _callbackContext = callbackContext;
        if ("startVoice".equals(action)) {
            if (cordova.hasPermission(AUDIO)) {
                startVoice();
            } else {
                this.getPermission();
            }
        } else {
            return false;
        }
        return true;
    }

    private void startVoice() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                mIatDialog.setListener(mRecognizerDialogListener);
                mIatDialog.show();
            }
        });
    }



    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            initStatus = (code == ErrorCode.SUCCESS);
        }
    };

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            System.out.println("转换前====》"+results.getResultString());
            System.out.println("转换后====》"+parseIatResult(results.getResultString()));
            _callbackContext.success(parseIatResult(results.getResultString()));
        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            _callbackContext.error(error.getErrorCode());
        }

    };

    /**
     * 解析语音输出
     *
     * @return
     */
    private String parseIatResult(String json) {
        StringBuffer ret = new StringBuffer();
        try {
            JSONTokener tokener = new JSONTokener(json);
            JSONObject joResult = new JSONObject(tokener);

            JSONArray words = joResult.getJSONArray("ws");
            for (int i = 0; i < words.length(); i++) {
                // 转写结果词，默认使用第一个结果
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                JSONObject obj = items.getJSONObject(0);
                ret.append(obj.getString("w"));
                //              如果需要多候选结果，解析数组其他字段
                //              for(int j = 0; j < items.length(); j++)
                //              {
                //                  JSONObject obj = items.getJSONObject(j);
                //                  ret.append(obj.getString("w"));
                //              }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret.toString();
    }

    /**
     * 参数设置
     * 
     * @return
     */
    public void setParam() {
        // 清空参数
        mIat.setParameter(SpeechConstant.PARAMS, null);

        // 设置听写引擎
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        // 设置返回结果格式
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        // 设置语言
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        // 设置语言区域
        // mIat.setParameter(SpeechConstant.ACCENT, "translate");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIat.setParameter(SpeechConstant.VAD_BOS, "4000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIat.setParameter(SpeechConstant.VAD_EOS, "1000");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, "1");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");

        Activity activity = cordova.getActivity();
        String packageName = activity.getPackageName();
        String audioPath = "";
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            audioPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + packageName
                    + "/cache/";
        } else {
            audioPath = "/data/data/" + packageName;
        }
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, audioPath + "/msc/iat.wav");
    }

    protected void getPermission() {
        cordova.requestPermissions(this, 0, new String[] { AUDIO });
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
            throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                _callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }

        switch (requestCode) {
        case REQ_CODE:
            startVoice();
            break;
        }

    }
}

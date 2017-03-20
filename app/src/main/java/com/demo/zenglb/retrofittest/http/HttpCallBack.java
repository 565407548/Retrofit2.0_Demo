package com.demo.zenglb.retrofittest.http;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.support.annotation.CallSuper;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.demo.zenglb.retrofittest.R;
import com.demo.zenglb.retrofittest.utils.TextUtils;
import com.demo.zenglb.retrofittest.utils.httpUtils.HttpDialogUtils;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 这里的封装处理方式和api 协议结构有关。因为我们的Api 不是那么的restful-需要再处理一下
 * <p>
 * 一般的Success 的处理各不相同，但是fail会有很多相同的处理方式
 * 一定要处理好各种异常情况。
 */

public abstract class HttpCallBack<T extends HttpResponse> implements Callback<T> {
	private final String TAG = HttpCallBack.class.getSimpleName();
	private static Gson gson = new Gson();
	private Context mContext;
	private final int RESPONSE_CODE_OK = 0;      //自定义的业务逻辑，成功返回积极数据
	private final int RESPONSE_CODE_FAILED = -1; //返回数据失败

	//是否需要显示Http 请求的进度，默认的是需要，但是Service 和 预取数据不需要
	private boolean showProgress = true;

	/**
	 * @param mContext
	 */
	public HttpCallBack(Context mContext) {
		this.mContext = mContext;
		if (showProgress) {
			//show your progress bar
			showDialog(true, "loading...");
		}
	}

	/**
	 * @param mContext
	 * @param showProgress 默认需要显示进程，不要的话请传 false
	 */
	public HttpCallBack(Context mContext, boolean showProgress) {
		this.showProgress = showProgress;
		this.mContext = mContext;
		if (showProgress) {
			showDialog(true, null);
		}
	}


	public abstract void onSuccess(T t);

	/**
	 * Default error dispose!
	 * 一般的就是 AlertDialog 或 SnackBar
	 *
	 * @param code
	 * @param message
	 */
	@CallSuper  //if overwrite,you should let it run.
	public void onFailure(int code, String message) {
		if (code == RESPONSE_CODE_FAILED && mContext != null) {
			alertTip(message, code);
		} else {
			disposeEorCode(message, code);
		}
	}


	/**
	 * Invoked for a received HTTP response.
	 * <p>
	 * Note: An HTTP response may still indicate an application-level failure such as a 404 or 500.
	 * Call {@link Response#isSuccessful()} to determine if the response indicates success.
	 */
	@Override
	public final void onResponse(Call<T> call, Response<T> response) {
		dismissDialog();
		if (response.isSuccessful()) {  //mean that   code >= 200 && code < 300
			int responseCode = response.body().getCode();
			//responseCode是业务api 里面定义的,根据responseCode进行进一步的数据和事件分发!
			if (responseCode == RESPONSE_CODE_OK) {
				onSuccess(response.body());
			} else {
				onFailure(responseCode, response.body().getError());
			}
		} else {
			//================ 1.handle http default error 4xx,5xx=================
			int code = response.raw().code();
			String message = response.raw().message();   //code 和 message 都是http Raw 数据，你抓包就能看见的
			Log.e("http-error", "code:" + code + "   message:" + message);
			//我们的项目返回404 的时候有可能是翻页到没有数据了,这一点很恶心
			if (code != 404) {
				onFailure(code, message);
				return;
			}

			//================ 2.把项目业务方面定义的错误提取处理处理，和业务逻辑，api 有关=================
			String errorBodyStr = "";
			try {   //我们的项目需要的UniCode转码，不是必须要的！
				errorBodyStr = TextUtils.convertUnicode(response.errorBody().string());
			} catch (IOException ioe) {
				Log.e("errorBodyStr ioe:", ioe.toString());
			}
			try {
				HttpResponse errorResponse = gson.fromJson(errorBodyStr, HttpResponse.class);
				if (null != errorResponse) {
					onFailure(errorResponse.getCode(), errorResponse.getError());
					//这里的code 如果定义和public void onFailure(Call<T> call, Throwable t) { 一样，要记得分开处理
				} else {
					onFailure(RESPONSE_CODE_FAILED, "ErrorResponse is null ");  //!!!!!!
				}
			} catch (Exception jsonException) {
				onFailure(RESPONSE_CODE_FAILED, "http请求错误Json 信息异常"); //
				jsonException.printStackTrace();
			}
		}//response is not Successful dispose over !
	}

	/**
	 * 区别处理Htpp error 和 业务逻辑的Error code ,如果有重复，需要区别处理
	 * <p>
	 * Invoked when a network exception occurred talking to the server or when an unexpected
	 * exception occurred creating the request or processing the response.
	 */
	@Override
	public final void onFailure(Call<T> call, Throwable t) {
		dismissDialog();
		String temp = t.getMessage().toString();

		String errorMessage = "获取数据失败[def-error]" + temp;
		if (t instanceof SocketTimeoutException) {
			errorMessage = "服务器响应超时";
		} else if (t instanceof ConnectException) {
			errorMessage = "网络连接异常，请检查网络";
		} else if (t instanceof RuntimeException) {
			errorMessage = "运行时错误";
		} else if (t instanceof UnknownHostException) {
			errorMessage = "无法解析主机，请检查网络连接";
		} else if (t instanceof UnknownServiceException) {
			errorMessage = "未知的服务器错误";
		}
		onFailure(RESPONSE_CODE_FAILED, errorMessage);
	}


	/**
	 * 对通用问题的统一拦截处理
	 *
	 * @param code
	 */
	private void disposeEorCode(String message, int code) {
		switch (code) {
			case 101:
			case 112:
			case 123:
			case 401:
				//退回到登录页面，
//				Intent intent = new Intent();
//				intent.setClass(mContext, LoginActivity.class);
//				mContext.startActivity(intent);
				break;
		}
		Toast.makeText(mContext, message + " # " + code, Toast.LENGTH_SHORT).show();
	}


	/**
	 * http 请求遇阻提示，比如没有网络不提示，再重试也无用
	 */
	private void alertTip(String message, int code) {
		//// TODO: 2017/3/14 这里的提示框需要做成单例的,连续弹出来烦死了
		AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
		builder.setTitle("获取数据失败");
		builder.setMessage(message);
		builder.setPositiveButton("知道了", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
			}
		});

		AlertDialog dlg = builder.create();
		dlg.show();
		dlg.setCanceledOnTouchOutside(false);

		dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
		dlg.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(mContext, R.color.colorPrimary));
	}


	/**
	 * showDialog & dismissDialog 在http 请求开始的时候显示，结束的时候消失
	 * 当然不是必须需要显示的 !
	 */
	private final void showDialog(final boolean canceledOnTouchOutside, final String messageText) {
		if (mContext == null || mContext == null || ((Activity) mContext).isFinishing())
			return;
		((Activity) mContext).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				HttpDialogUtils.showDialog(mContext, canceledOnTouchOutside, messageText);
			}
		});
	}

	private final void dismissDialog() {
		if (mContext == null || ((Activity) mContext).isFinishing())
			return;             //maybe not good !
		if (mContext != null) {
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					HttpDialogUtils.dismissDialog();
				}
			});
		}
	}

}

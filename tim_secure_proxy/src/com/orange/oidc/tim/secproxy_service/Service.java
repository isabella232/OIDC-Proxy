/*
* 
* Copyright (C) 2015 Orange Labs
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*    http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* 
*/

package com.orange.oidc.tim.secproxy_service;

import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import com.orange.oidc.tim.secproxy_service.IRemoteListenerToken;
import com.orange.oidc.tim.secproxy_service.IRemoteService;
import com.orange.oidc.tim.secproxy_service.IRemoteServiceInternal;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


/**
 * Openid Connect proxy service class
 *
 */
public class Service extends android.app.Service {

	protected static final String TAG = "Service";
	
	final static String EMPTY = "";
	
	static {
	    Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
	}

	int idList=0;
	class RemoteListenerToken {
		IRemoteListenerToken listener;
		String id;
		OpenidConnectParams ocp;
		RemoteListenerToken(IRemoteListenerToken r) {
			listener = r;
			idList++;
			id = ""+idList;
		}
	};
	
	List <RemoteListenerToken> RemoteListenerTokenList = new ArrayList<RemoteListenerToken>();
	
	public static Service theService = null;
	
	private static TimSecureStorage secureStorage;
	
	public Service() {
		// android.os.Debug.waitForDebugger();
		System.setProperty("http.keepAlive", "false");
		if( theService == null ) {
			theService = this;
			// init secure storage
			secureStorage = new SDCardStorage();
			
			// init Http
			HttpOpenidConnect.secureStorage = secureStorage;
		}
		
	}

	// private final IRemoteServiceBinder mBinder = new IRemoteServiceBinder();

	public class ServiceBinder extends Binder {
		Service getService() {
			return Service.this;
		}
	}

	private final IRemoteService.Stub mBinder = new IRemoteService.Stub() {
		
		@Override
		public void getTokensWithTimProxy(
		        IRemoteListenerToken listener,
		        String serverUrl,
		        String client_id,
				String scope,
				String state,
		        String nonce )
				throws RemoteException {

			Logd(TAG,"getTokensWithTimProxy begin");

			showNotProtectedNotifIcon();
			
			if(!serverUrl.endsWith("/")) serverUrl += "/";
			
			scope = sortScope(scope+" tim");
			
	        OpenidConnectParams ocp = new OpenidConnectParams(serverUrl, client_id, scope, secureStorage.getRedirectUri(), state, nonce, null, null, null);

			Logd(TAG,"ocp: "+ocp.toString());
	        
	        RemoteListenerToken rl;
			synchronized(RemoteListenerTokenList) {
				rl = new RemoteListenerToken(listener);
				rl.ocp = ocp;
				RemoteListenerTokenList.add(rl);
			}

			// launch request
	        HttpOpenidConnect hc = new HttpOpenidConnect(ocp);

			if( ! hc.getTokens( Service.this, rl.id, null )  ) {
				setClientTokens(rl.id,null);
			}

			Logd(TAG,"getTokensWithTimProxy end");

		}

	    @Override
	    public String webFinger(
	            String userInput,
	            String serverUrl
	            ) {
			try {
				return HttpOpenidConnect.webfinger(userInput, serverUrl);
			} catch (Exception e) {
				
			}
			return null;
		}
 
		@Override
		public String getUserInfo(
			String serverUrl,
			String access_token ) {
			return HttpOpenidConnect.getUserInfo(serverUrl, access_token);
		}

		// Logout from the idp
		@Override
	    public void logout(String serverUrl) {
			HttpOpenidConnect.logout(serverUrl, null);
		}

	};

	void resetCookies() {
    	// init intent
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(Service.this, WebViewActivity.class);

        intent.putExtra("resetcookies", "true" );
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        // launch webview
        startActivity(intent);
	}
	
	long getSecretPathThreshold() {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		long l= sharedPrefs.getLong("threshold", 80);
		return l;
	}
	
	void setSecretPathThreshold(long val) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		Editor editor = sharedPrefs.edit();
		editor.putLong("threshold", val);
		editor.commit();
		Logd(TAG,"threshold: "+val);
	}
	
	// internal remote service for internal webview
	private final IRemoteServiceInternal.Stub mBinderInternal = new IRemoteServiceInternal.Stub() {

		private void setTokensRedirect(String id, HttpOpenidConnect hc, String redirect) {
			// android.os.Debug.waitForDebugger();

			// check caller uid
			if( checkCallingUid() == false ) {
				// caller are not from inside this app
				Logd(TAG,"setTokensRedirect not from inside this app");
				return;
			}

			RemoteListenerToken rl=null;
			synchronized(RemoteListenerTokenList) {
				for(int i=RemoteListenerTokenList.size()-1; i>=0; i--) {
					RemoteListenerToken r = RemoteListenerTokenList.get(i);
					if(r.id.compareTo(id)==0) {
						rl = r;
						RemoteListenerTokenList.remove(i);
						break;
					}
				}
			}
			
			if( rl != null  ) {
		        try {
		        	String tokens = hc.doRedirect(redirect);

		        	JSONObject jObject = null;
		        	try {
		        		jObject = new JSONObject(tokens);
		        	} catch (JSONException je){
		        		// check if it is JWE
		        		tokens = secureStorage.decryptJWE( tokens );
		        		Logd(TAG,"doRedirect JWE: "+tokens);
		        		jObject = new JSONObject(tokens);
		        	}

		        	boolean user_cancel = false;
		        	String userCancel   = getFromJS( jObject, "cancel" );
		        	if(userCancel!=null && userCancel.equalsIgnoreCase("true") ) {
		        		user_cancel = true;
		        	}
		        	
		        	// put id_token and refresh_token in Secure Storage
		        	if(user_cancel==false) {
			        	String access_token  = getFromJS( jObject, "access_token" );
			        	// String token_type    = getFromJS( jObject, "token_type" );
			        	String refresh_token = getFromJS( jObject, "refresh_token" );
			        	String expires_in    = getFromJS( jObject, "expires_in" );
			        	String id_token      = getFromJS( jObject, "id_token" );
			        	rl.ocp.m_server_scope= getFromJS( jObject, "scope" );
		        		rl.listener.handleTokenResponseWithTimProxy(id_token, access_token, false );
						return;


		        	} else {
		        		rl.listener.handleTokenResponseWithTimProxy(EMPTY, EMPTY, true );
		        		return;
		        	}
		        	
		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		        
		        // if here, then nothing to notify
				if( rl.listener!=null ) {
					try {
		        		rl.listener.handleTokenResponseWithTimProxy(EMPTY, EMPTY, false );
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}

		}
		
		@Override
		public void cancel(String id, boolean user) throws RemoteException {
			
			// check caller uid
			if( checkCallingUid() == false ) {
				// caller are not from inside this app
				Logd(TAG,"setTokens not from inside this app");
				return;
			}

			RemoteListenerToken rl=null;
			synchronized(RemoteListenerTokenList) {
				for(int i=RemoteListenerTokenList.size()-1; i>=0; i--) {
					RemoteListenerToken r = RemoteListenerTokenList.get(i);
					if(r.id.compareTo(id)==0) {
						rl = r;
						RemoteListenerTokenList.remove(i);
						break;
					}
				}
			}
			
			if( rl != null  ) {
        		rl.listener.handleTokenResponseWithTimProxy(EMPTY, EMPTY, user );
			}

			// hideNotifIcon();
		}

		@Override
		public void doRedirect( String id, String redirect ) {
			
			// check caller uid
			if( checkCallingUid() == false ) {
				// caller are not from inside this app
				Logd(TAG,"doRedirect not from inside this app");
				return;
			}

			Logd(TAG,"doRedirect begin");
			
			if(id==null || id.length()==0 ) {
				Logd(TAG,"doRedirect end no ID");
				// hideNotifIcon();
				return;
			}
			
	        OpenidConnectParams ocp = null;
			// android.os.Debug.waitForDebugger();
			
			synchronized(RemoteListenerTokenList) {
				for(int i=RemoteListenerTokenList.size()-1; i>=0; i--) {
					RemoteListenerToken r = RemoteListenerTokenList.get(i);
					if(r.id.compareTo(id)==0) {
				        ocp = new OpenidConnectParams(r.ocp);
						break;
					}
				}
			}

			if( ocp != null ) {
		        HttpOpenidConnect hc = new HttpOpenidConnect(ocp);

		        try { 
		        	setTokensRedirect(id, hc, redirect );
		        	return;
		        } catch(Exception e) {
		        	e.printStackTrace();
		        }
			}
			
			// if error, set null response
	        try { 
	        	cancel(id,false);
	        } catch(Exception e) {
	        	e.printStackTrace();
	        }

	        Logd(TAG,"doRedirect end");
			// hideNotifIcon();
		}

		@Override
		public void resetCookies() throws RemoteException {
			
			// check caller uid
			if( checkCallingUid() == false ) {
				// caller are not from inside this app
				Logd(TAG,"resetCookies not from inside this app");
				return;
			}

			// clear cookies
	        // android.webkit.CookieManager.getInstance().removeAllCookie();
		}

		// check calling process uid, if different return false
		// possibility of hack
		private boolean checkCallingUid() {
			// check uid
			if( android.os.Process.myUid() == Binder.getCallingUid() ) {
				return true;
			}
			
			// uid are not the same
			return false;
		}

	};

	@Override
	public void onCreate() {
		super.onCreate();
	}

	// service termination
	@Override
	public void onDestroy() {
		hideNotifIcon();
		super.onDestroy();
	}
	
	// service starting
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
	}

	// new connection to service
    @Override
    public IBinder onBind(Intent intent) {
        // Select the interface to return.  If your service only implements
        // a single interface, you can just return it here without checking
        // the Intent.
        if (IRemoteService.class.getName().equals(intent.getAction())) {
    		showProtectedNotifIcon();
    		Logd(TAG,"onBind Service");
            return mBinder;
        }
        if (IRemoteServiceInternal.class.getName().equals(intent.getAction())) {
    		Logd(TAG,"onBind ServiceInternal");
            return mBinderInternal;
        }
        return null;
    }

	void setClientTokens(String id, String tokens) {
		Logd(TAG,"setClientTokens id:"+id);
		Logd(TAG,"setClientTokens tokens:"+tokens);
		RemoteListenerToken rl=null;
		synchronized(RemoteListenerTokenList) {
			for(int i=RemoteListenerTokenList.size()-1; i>=0; i--) {
				RemoteListenerToken r = RemoteListenerTokenList.get(i);
				if(r.id.compareTo(id)==0) {
					rl = r;
					RemoteListenerTokenList.remove(i);
					break;
				}
			}
		}
		
		if( rl != null  ) {

        	JSONObject jObject = null;
	        try {
	        	if(tokens!=null)
	        		jObject = new JSONObject(tokens);

	        	if(jObject!=null) {
		        	String access_token  = getFromJS( jObject, "access_token" );
		        	// String token_type    = getFromJS( jObject, "token_type" );
		        	String refresh_token  = getFromJS( jObject, "refresh_token" );
		        	String expires_in     = getFromJS( jObject, "expires_in" );
		        	String id_token       = getFromJS( jObject, "id_token" );
		        	rl.ocp.m_server_scope = getFromJS( jObject, "scope" );
		        	boolean user_cancel = false;
		        	String userCancel   = getFromJS( jObject, "cancel" );
		        	if(userCancel!=null && userCancel.equalsIgnoreCase("true") ) {
		        		user_cancel = true;
		        	}
		        	
					rl.listener.handleTokenResponseWithTimProxy( id_token, access_token, user_cancel );
					return;
	        	}
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}
		
		if(rl!=null && rl.listener!=null ) {
			try {
        		rl.listener.handleTokenResponseWithTimProxy(EMPTY, EMPTY, false );
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}
		
	}
	
	
	// get a string from a json object
	String getFromJS(JSONObject jo, String name){
		if ( jo != null ) {
			try {
				return jo.getString(name);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	String sortScope(String scope) {
		// sort scope in alphabetical order
		if( scope != null ) {
    		scope = scope.toLowerCase(Locale.getDefault());
    		// offline_access is mandatory
    		if ( !scope.contains("offline_access") ) {
    			scope += " offline_access";
    		}
    		/*
    		// and tim too for php oidc
    		if ( !scope.contains("tim") ) {
    			scope += " tim";
    		}
    		*/
    		String scopes[] = scope.split("\\ ");
    		if(scopes!=null) {
    			Arrays.sort(scopes, new Comparator<String>() {
    				 @Override
    				 public int compare(String s1, String s2) {
    				    return s1.compareToIgnoreCase(s2);
    				    }
    				 });
				scope = null;
				// filter null or empty strings
    			for(int i=0; i<scopes.length; i++) {
    				if( scopes[i] != null && scopes[i].length()>0 ) {
	    				if(scope==null)
	    					scope = scopes[i];
	    				else
	    					scope += ( " " + scopes[i] );
    				}
    			}
    		}
		}
		return scope;
	}
	
	void toast(final String msg, final int duration) {
		new android.os.Handler(android.os.Looper.getMainLooper())
				.post(new Runnable() {
					@Override
					public void run() {
						android.widget.Toast
								.makeText(
										theService,
										msg,
										duration == 0 ? android.widget.Toast.LENGTH_SHORT
												: android.widget.Toast.LENGTH_LONG)
								.show();
					}
				});
	}
	
	
	private void showProtectedNotifIcon() {
		showNotification(true);
	}

	private void showNotProtectedNotifIcon() {
		showNotification(false);
	}
	
	private void showNotification(boolean bProtect){
		Logd(TAG,"show protected icon "+bProtect);

        // this is it, we'll build the notification!
        // in the addAction method, if you don't want any icon, just set the first param to 0
        Notification mNotification = null;
        
        if(bProtect) {
        	mNotification = new Notification.Builder(this)

            .setContentTitle("TIM")
            .setContentText("privacy protected")
            .setSmallIcon(R.drawable.masked_on)
            .setAutoCancel(false)
            .build();
        } else {
        	mNotification = new Notification.Builder(this)

            .setContentTitle("TIM")
            .setContentText("privacy not protected")
            .setSmallIcon(R.drawable.masked_off)
            .setAutoCancel(false)
            .build();
        }

        // to make it non clearable
        mNotification.flags |= Notification.FLAG_NO_CLEAR;
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // If you want to hide the notification after it was selected, do the code below
        // myNotification.flags |= Notification.FLAG_AUTO_CANCEL;

        notificationManager.notify(0, mNotification);
    }

    private void hideNotifIcon() {
		Logd(TAG,"hideNotifIcon");

        if (Context.NOTIFICATION_SERVICE!=null) {
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager nMgr = (NotificationManager) getApplicationContext().getSystemService(ns);
            nMgr.cancel(0);
        }
    }

    void Logd(String tag, String msg) {
		if(tag!=null && msg!=null) Log.d(tag, msg);
	}

}

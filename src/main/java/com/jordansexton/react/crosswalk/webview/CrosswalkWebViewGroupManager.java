package com.jordansexton.react.crosswalk.webview;

import android.app.Activity;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import org.xwalk.core.XWalkNavigationHistory;
import org.xwalk.core.XWalkView;
import com.facebook.react.bridge.ReactContext;
import javax.annotation.Nullable;
import java.util.Map;

public class CrosswalkWebViewGroupManager extends ViewGroupManager<CrosswalkWebView> {

    public static final int GO_BACK = 1;

    public static final int GO_FORWARD = 2;

    public static final int RELOAD = 3;
   
    public static final int COMMAND_INJECT_BRIDGE_SCRIPT = 100;
  public static final int COMMAND_SEND_TO_BRIDGE = 101;

    @VisibleForTesting
    public static final String REACT_CLASS = "CrosswalkWebView";

    private ReactApplicationContext reactContext;

    private static final String BLANK_URL = "about:blank";
private boolean initializedBridge;
    public CrosswalkWebViewGroupManager (ReactApplicationContext _reactContext) {
        reactContext = _reactContext;
        initializedBridge= false;
    }

    @Override
    public String getName () {
        return REACT_CLASS;
    }

    @Override
    public CrosswalkWebView createViewInstance (ThemedReactContext context) {
        Activity _activity = reactContext.getCurrentActivity();
        CrosswalkWebView crosswalkWebView = new CrosswalkWebView(context, _activity);
        context.addLifecycleEventListener(crosswalkWebView);
        return crosswalkWebView;
    }

    @Override
    public void onDropViewInstance(CrosswalkWebView view) {
        super.onDropViewInstance(view);
        ((ThemedReactContext) view.getContext()).removeLifecycleEventListener((CrosswalkWebView) view);
        view.onDestroy();
    }

    @ReactProp(name = "source")
    public void setSource(final CrosswalkWebView view, @Nullable ReadableMap source) {
      Activity _activity = reactContext.getCurrentActivity();
      if (_activity != null) {
          if (source != null) {
              if (source.hasKey("html")) {
                  final String html = source.getString("html");
                  _activity.runOnUiThread(new Runnable() {
                      @Override
                      public void run () {
                          view.load(null, html);
                      }
                  });
                  return;
              }
              if (source.hasKey("uri")) {
                  final String url = source.getString("uri");
                  _activity.runOnUiThread(new Runnable() {
                      @Override
                      public void run () {
                          view.load(url, null);
                      }
                  });
                  return;
              }
          }
      }
      setUrl(view, BLANK_URL);
    }


    @ReactProp(name = "injectedJavaScript")
    public void setInjectedJavaScript (XWalkView view, @Nullable String injectedJavaScript) {
        ((CrosswalkWebView) view).setInjectedJavaScript(injectedJavaScript);
    }

    @ReactProp(name = "url")
    public void setUrl (final CrosswalkWebView view, @Nullable final String url) {
        Activity _activity = reactContext.getCurrentActivity();
        if (_activity != null) {
            _activity.runOnUiThread(new Runnable() {
                @Override
                public void run () {
                    view.load(url, null);
                }
            });
        }
    }

    @ReactProp(name = "localhost")
    public void setLocalhost (CrosswalkWebView view, Boolean localhost) {
        view.setLocalhost(localhost);
    }

    @Override
    public
    @Nullable
    Map<String, Integer> getCommandsMap () {
        return MapBuilder.of(
            // "goBack", GO_BACK,
            // "goForward", GO_FORWARD,
            // "reload", RELOAD,
            "injectBridgeScript", COMMAND_INJECT_BRIDGE_SCRIPT,
            "sendToBridge", COMMAND_SEND_TO_BRIDGE
        );
    }

    @Override
    public void receiveCommand (CrosswalkWebView view, int commandId, @Nullable ReadableArray args) {
        super.receiveCommand(view, commandId, args);

        switch (commandId) {
            case GO_BACK:
                view.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.BACKWARD, 1);
                break;
            case GO_FORWARD:
                view.getNavigationHistory().navigate(XWalkNavigationHistory.Direction.FORWARD, 1);
                break;
            case RELOAD:
                view.reload(XWalkView.RELOAD_NORMAL);
                break;
            case COMMAND_INJECT_BRIDGE_SCRIPT:
                injectBridgeScript(view);
                break;
            case COMMAND_SEND_TO_BRIDGE:
                sendToBridge(view, args.getString(0));
                break;
        }
    }

    @Override
    public Map getExportedCustomDirectEventTypeConstants () {
        return MapBuilder.of(
            NavigationStateChangeEvent.EVENT_NAME,
            MapBuilder.of("registrationName", "onNavigationStateChange"),
            ErrorEvent.EVENT_NAME,
            MapBuilder.of("registrationName", "onError")
        );
    }

    private void sendToBridge(CrosswalkWebView root, String message) {
    //root.loadUrl("javascript:(function() {\n" + script + ";\n})();");
    String script = "WebViewBridge.onMessage('" + message + "');";
    CrosswalkWebViewGroupManager.evaluateJavascript(root, script);
  }

  private void injectBridgeScript(CrosswalkWebView root) {
    //this code needs to be called once per context
    if (!initializedBridge) {
      root.addJavascriptInterface(new JavascriptBridge((ReactContext) root.getContext()), "WebViewBridgeAndroid");
      initializedBridge = true;
      root.reload(XWalkView.RELOAD_NORMAL);
    }

    // this code needs to be executed everytime a url changes.
    CrosswalkWebViewGroupManager.evaluateJavascript(root, ""
            +"alert('eval js from java');"
            + "(function() {"
            + "if (window.WebViewBridge) return;"
            + "var customEvent = document.createEvent('Event');"
            + "var WebViewBridge = {"
              + "send: function(message) { WebViewBridgeAndroid.send(message); },"
              + "onMessage: function() {}"
            + "};"
            + "window.WebViewBridge = WebViewBridge;"
            + "customEvent.initEvent('WebViewBridge', true, true);"
            + "document.dispatchEvent(customEvent);"
            + "}());");
  }

  static private void evaluateJavascript(CrosswalkWebView root, String javascript) {
    // root.evaluateJavascript(javascript, null);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
      root.evaluateJavascript(javascript, null);
    } else {
      root.load("javascript:" + javascript,null);
    }
  }
}

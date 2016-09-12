'use strict';

import React, { PropTypes } from 'react';
import ReactNative, { requireNativeComponent, View, DeviceEventEmitter } from 'react-native';
var keyMirror = require('keymirror');
import PureRenderMixin from "react-addons-pure-render-mixin";
var {
    // addons: { PureRenderMixin },
    NativeModules: { UIManager, CrosswalkWebViewManager: { JSNavigationScheme } }
} = ReactNative;

var resolveAssetSource = require('react-native/Libraries/Image/resolveAssetSource');

var WEBVIEW_REF = 'crosswalkWebView';
var WebViewBridgeState = keyMirror({
    IDLE: null,
    LOADING: null,
    ERROR: null,
});
var CrosswalkWebView = React.createClass({
    mixins: [PureRenderMixin],
    statics: { JSNavigationScheme },
    propTypes: {
        injectedJavaScript: PropTypes.string,
        localhost: PropTypes.bool.isRequired,
        onError: PropTypes.func,
        onNavigationStateChange: PropTypes.func,
        source: PropTypes.oneOfType([
            PropTypes.shape({
                uri: PropTypes.string,  // URI to load in WebView
            }),
            PropTypes.shape({
                html: PropTypes.string, // static HTML to load in WebView
            }),
            PropTypes.number,           // used internally by React packager
        ]),
        url: PropTypes.string,
        ...View.propTypes,
    /**
 * Will be called once the message is being sent from webview
 */
    onBridgeMessage: PropTypes.func,
    },
getInitialState() {
    return {
        viewState: WebViewBridgeState.IDLE,
        lastErrorEvent: null,
        startInLoadingState: true,
    };
},
getDefaultProps() {
    return {
        localhost: false
    };
},   
componentWillMount() {
    DeviceEventEmitter.addListener("webViewBridgeMessage", (body) => {
        const { onBridgeMessage } = this.props;
        const message = body.message;
        if (onBridgeMessage) {
            onBridgeMessage(message);
        }
    });

    if (this.props.startInLoadingState) {
        this.setState({ viewState: WebViewBridgeState.LOADING });
    }
},
render() {
    var source = this.props.source || {};
    if (this.props.url) {
        source.uri = this.props.url;
    }
    return (
        <NativeCrosswalkWebView
            { ...this.props }
            onError={ this.onError }
            onNavigationStateChange={ this.onNavigationStateChange }
            ref={ WEBVIEW_REF }
            source={ resolveAssetSource(source) }
            onLoadStarted={this.onLoadingStart}
            onLoadFinished={this.onLoadingFinish}
            onReceivedLoadError={this.onLoadingError}
            />
    );
},
getWebViewHandle() {
    return React.findNodeHandle(this.refs[WEBVIEW_REF]);
},
onNavigationStateChange(event) {
    var { onNavigationStateChange } = this.props;
    if (onNavigationStateChange) {
        onNavigationStateChange(event.nativeEvent);
    }
},
onError(event) {
    var { onError } = this.props;
    if (onError) {
        onError(event.nativeEvent);
    }
},
goBack() {
    UIManager.dispatchViewManagerCommand(
        this.getWebViewHandle(),
        UIManager.NativeCrosswalkWebView.Commands.goBack,
        null
    );
},
goForward() {
    UIManager.dispatchViewManagerCommand(
        this.getWebViewHandle(),
        UIManager.NativeCrosswalkWebView.Commands.goForward,
        null
    );
},
reload() {
    UIManager.dispatchViewManagerCommand(
        this.getWebViewHandle(),
        UIManager.NativeCrosswalkWebView.Commands.reload,
        null
    );
},

 sendToBridge: function (message: string) {
     console.log('[crosswalk] sendToBridge,message:'+message);
    UIManager.dispatchViewManagerCommand(
      this.getWebViewBridgeHandle(),
      UIManager.RCTWebViewBridge.Commands.sendToBridge,
      [message]
    );
  },

  injectBridgeScript: function () {
    console.log('[crosswalk] injectBridgeScript');
    UIManager.dispatchViewManagerCommand(
      this.getWebViewBridgeHandle(),
      UIManager.RCTWebViewBridge.Commands.injectBridgeScript,
      null
    );
  },
/**
   * We return an event with a bunch of fields including:
   *  url, title, loading, canGoBack, canGoForward
   */
updateNavigationState(event) {
    if (this.props.onNavigationStateChange) {
        this.props.onNavigationStateChange(event.nativeEvent);
    }
},

getWebViewBridgeHandle() {
    return ReactNative.findNodeHandle(this.refs[WEBVIEW_REF]);
},

onLoadingStart(event) {
    console.log('[crosswalk] onLoadingStart');
    this.injectBridgeScript();
    var onLoadStart = this.props.onLoadStart;
    onLoadStart && onLoadStart(event);
    this.updateNavigationState(event);
},

onLoadingError(event) {
    console.log('[crosswalk] onLoadingError');
    event.persist(); // persist this event because we need to store it
    var {onError, onLoadEnd} = this.props;
    onError && onError(event);
    onLoadEnd && onLoadEnd(event);
    console.error('Encountered an error loading page', event.nativeEvent);

    this.setState({
        lastErrorEvent: event.nativeEvent,
        viewState: WebViewBridgeState.ERROR
    });
},

onLoadingFinish(event) {
    console.log('[crosswalk] onLoadingFinish');
    var {onLoad, onLoadEnd} = this.props;
    onLoad && onLoad(event);
    onLoadEnd && onLoadEnd(event);
    this.setState({
        viewState: WebViewBridgeState.IDLE,
    });
    this.updateNavigationState(event);
},

});

var NativeCrosswalkWebView = requireNativeComponent('CrosswalkWebView', CrosswalkWebView);

export default CrosswalkWebView;

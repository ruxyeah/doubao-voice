package com.doubao.voice.client;

/**
 * 重连事件监听器
 *
 * 扩展DoubaoClientListener，提供重连相关事件的回调
 */
public interface ReconnectListener extends DoubaoClientListener {

    /**
     * 开始重连
     *
     * @param attempt 当前重连次数
     * @param maxAttempts 最大重连次数
     * @param delayMs 延迟时间（毫秒）
     */
    default void onReconnecting(int attempt, int maxAttempts, long delayMs) {
    }

    /**
     * 重连成功
     *
     * @param attempts 重连次数
     */
    default void onReconnected(int attempts) {
    }

    /**
     * 重连失败（达到最大重连次数）
     *
     * @param attempts 已尝试次数
     */
    default void onReconnectFailed(int attempts) {
    }

    /**
     * 连接超时（发送请求后长时间无响应）
     *
     * @param pendingRequests 等待响应的请求数
     * @param lastSendAgo 距离最后发送的时间（毫秒）
     * @param lastReceiveAgo 距离最后接收的时间（毫秒）
     */
    default void onConnectionTimeout(int pendingRequests, long lastSendAgo, long lastReceiveAgo) {
    }
}

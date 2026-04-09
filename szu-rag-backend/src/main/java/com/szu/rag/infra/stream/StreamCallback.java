package com.szu.rag.infra.stream;

/**
 * LLM 流式输出回调
 */
public interface StreamCallback {

    /** 收到一段文本内容 */
    default void onContent(String content) {}

    /** 模型开始思考/检索 */
    default void onThinking(String thinking) {}

    /** 流式完成 */
    default void onComplete(String fullResponse) {}

    /** 发生错误 */
    default void onError(Throwable error) {}

    /** 是否已取消 */
    default boolean isCancelled() { return false; }
}

package com.openbe.queen.web;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 日志流处理器。
 * 客户端连接 /api/logs/stream 后，可实时接收 GREEN / YELLOW 车道的信息素流动日志。
 */
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        broadcast("{\"type\":\"sys\",\"msg\":\"[HIVE] WebSocket 连接成功，实时日志开始推送\"}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端发来的消息忽略
    }

    /**
     * 向所有已连接客户端广播消息（线程安全）。
     * @param json 格式：{"type":"log","lane":"GREEN","icon":"📤","msg":"...","time":"HH:mm:ss"}
     */
    public void broadcast(String json) {
        if (sessions.isEmpty()) return;
        TextMessage msg = new TextMessage(json);
        sessions.removeIf(s -> {
            if (!s.isOpen()) return true;
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
                return false;
            } catch (Exception e) {
                return true;
            }
        });
    }
}

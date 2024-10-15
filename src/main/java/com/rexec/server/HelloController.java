package com.rexec.server;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.servlet.http.HttpServletRequest;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@RestController
@RequestMapping("/api")
@EnableScheduling
public class HelloController extends TextWebSocketHandler {

    private final Map<String, ClientInfo> clientMap = new HashMap<>(); // 存储客户端信息
    private final Map<String, WebSocketSession> sessions = new HashMap<>(); // 存储WebSocket会话

    // 用于存储客户端信息的内部类
    private static class ClientInfo {
        Map<String, Object> info; // 存储命令及其他信息
        long timeout; // 记录请求时间（以毫秒为单位）

        ClientInfo() {
            this.info = new HashMap<>();
            this.info.put("command", null);
            this.info.put("lastCommand", null);
            this.info.put("UUID", null);
            this.timeout = System.currentTimeMillis(); // 初始化为当前时间
        }
    }

    // 确保"客户端集合"中已经存在目标客户端
    public boolean SearchAndCreateClientMap(String clientIP) {
        if (clientMap.containsKey(clientIP)) {
            ClientInfo clientInfo = clientMap.get(clientIP);
            clientInfo.timeout = System.currentTimeMillis(); // 更新请求时间
            return true;
        }

        ClientInfo clientInfo = new ClientInfo(); // 创建新客户端信息
        clientMap.put(clientIP, clientInfo);
        return true;
    }

    // 客户端获取命令
    @GetMapping("/get-command")
    public String getCommand(HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        if (SearchAndCreateClientMap(clientIP)) {
            ClientInfo client = clientMap.get(clientIP);
            client.timeout = System.currentTimeMillis(); // 更新请求时间
            if (client.info.get("command") != null) {
                String command = client.info.get("command").toString();
                client.info.put("command", null);
                client.info.put("lastCommand", command);
                return command;
            }
        }
        return null;
    }

    // 定时清理超时的IP
    @Scheduled(fixedRate = 10000) // 每10秒执行一次
    public void cleanUpExpiredClients() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, ClientInfo>> iterator = clientMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, ClientInfo> entry = iterator.next();
            String clientIP = entry.getKey();
            ClientInfo clientInfo = entry.getValue();

            // 检查是否超过10秒没有请求
            if (currentTime - clientInfo.timeout > 10000) { // 超过10秒
                iterator.remove(); // 从Map中移除超时的客户端
                System.out.println("清除超时客户端: " + clientIP);
                // 清除对应的WebSocket会话
                sessions.remove(clientInfo.info.get("UUID"));
            }
        }
    }

    // 客户端返回命令执行结果
    @PostMapping("/post-result")
    public String postResult(HttpServletRequest request) {
        // 获取客户端的IP地址
        String clientIP = request.getRemoteAddr();

        String success = request.getParameter("success");
        String status = request.getParameter("status");
        String code = request.getParameter("code");
        String result = request.getParameter("result");
        System.out.println("执行状态: " + success + ", 结束状态: " + status + ", 状态码: " + code);

        if (SearchAndCreateClientMap(clientIP)) {
            ClientInfo client = clientMap.get(clientIP);
            String UUID = client.info.get("UUID").toString();
            String lastCommand = (String) client.info.get("lastCommand"); // 获取lastCommand
            // 找到最后一个分号的位置
            int lastIndex = lastCommand.lastIndexOf(';');

            // 如果找到分号，截取其之后的字符串
            if (lastIndex != -1) {
                lastCommand = lastCommand.substring(lastIndex + 1); // 去掉分号及其之前的字符
            }
            Map<String, String> stateMap = new HashMap<String, String>();
            stateMap.put("success", success);
            stateMap.put("status", status);
            stateMap.put("code", code);
            // 这里可以根据需要处理结果
            if ("nil".equals(success) && "exit".equals(status) && "65280".equals(code)) {
                sendMessageToClient(UUID, lastCommand + ": command not found", lastCommand, stateMap);
            } else if ("true".equals(success) && "exit".equals(status) && "0".equals(code)) {
                sendMessageToClient(UUID, result, lastCommand, stateMap);
            } else {
                sendMessageToClient(UUID, "unknown error", lastCommand, stateMap);
            }
        }
        return "";
    }

    // 向客户端发送消息
    private void sendMessageToClient(String UUID, String message, String lastCommand, Map<String, String> stateMap) {
        WebSocketSession session = sessions.get(UUID);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> unprocessJson = new HashMap<String, Object>();
                unprocessJson.put("message", message);
                unprocessJson.put("lastCommand", lastCommand);
                unprocessJson.put("stateMap", stateMap);
                String jsonMessage = new JSONObject(unprocessJson).toString();
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // String clientIP = session.getRemoteAddress().getAddress().getHostAddress();
        // sessions.put(clientIP, session); // 存储会话
    }

    @SuppressWarnings("null")
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String clientMessage = message.getPayload();
        // String respondIP = session.getRemoteAddress().getAddress().getHostAddress();
        JSONObject json = new JSONObject(clientMessage);
        String command = json.getString("command");
        String clientIP = json.getString("IP");
        String UUID = json.getString("UUID");
        sessions.put(UUID, session);
        if (SearchAndCreateClientMap(clientIP)) {
            System.out.println("收到WebSocket消息: " + clientMessage);
            ClientInfo client = clientMap.get(clientIP);
            client.info.put("command", command);
            client.info.put("UUID", UUID); // 存储UUID
        }
    }

    @SuppressWarnings("null")
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // String clientIP = session.getRemoteAddress().getAddress().getHostAddress();
        // for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
        // if (entry.getValue().equals(session)) {
        // sessions.remove(entry.getKey()); // 清除会话
        // System.out.println("WebSocket连接已关闭: " + entry.getKey());
        // }
        // }
    }
}
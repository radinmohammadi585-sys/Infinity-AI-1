package com.infinity.ai1;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executors;

public class ServerMain {
  public static final String APP_NAME = "Infinity AI 1";
  public static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8101"));

  public static void main(String[] args) throws Exception {
    Config.load();
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    server.createContext("/api/health", ServerMain::health);
    server.createContext("/api/info", ServerMain::info);
    server.createContext("/api/chat", ServerMain::chat);
    server.createContext("/api/auth/register", AuthController::register);
    server.createContext("/api/auth/login", AuthController::login);
    server.createContext("/api/languages", ServerMain::languages);
    server.createContext("/api/peers", ServerMain::peers);
    RouteRegistry.register(server);
    server.createContext("/", ServerMain::staticFiles);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    System.out.println("[" + APP_NAME + "] http://0.0.0.0:" + PORT);
  }

  static boolean isHead(HttpExchange ex) {
    return "HEAD".equalsIgnoreCase(ex.getRequestMethod());
  }

  static void health(HttpExchange ex) throws IOException {
    json(ex, 200, "{\"ok\":true,\"app\":\"" + APP_NAME + "\"}");
  }

  static void info(HttpExchange ex) throws IOException {
    json(ex, 200, "{\"ok\":true,\"app\":\"" + APP_NAME + "\",\"theme\":\"Dark Futuristic\"}");
  }

  static void languages(HttpExchange ex) throws IOException {
    json(ex, 200, LanguageService.listJson());
  }

  static void peers(HttpExchange ex) throws IOException {
    json(ex, 200, PeerBridge.peersJson());
  }

  static void chat(HttpExchange ex) throws IOException {
    if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
      ex.sendResponseHeaders(200, -1);
      ex.getResponseBody().close();
      return;
    }
    if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
      json(ex, 405, "{\"ok\":false}");
      return;
    }
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    json(ex, 200, ChatEngine.reply(body));
  }

  static void staticFiles(HttpExchange ex) throws IOException {
    String method = ex.getRequestMethod();
    if ("OPTIONS".equalsIgnoreCase(method)) {
      ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,HEAD,POST,OPTIONS");
      ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
      ex.sendResponseHeaders(204, -1);
      ex.getResponseBody().close();
      return;
    }
    String path = ex.getRequestURI().getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
    if (path.startsWith("/api/")) {
      json(ex, 404, "{\"ok\":false,\"error\":\"not found\"}");
      return;
    }
    path = path.replace("..", "");
    Path file = null;
    for (Path root : new Path[]{Paths.get("Frontend"), Paths.get("frontend"), Paths.get(".")}) {
      Path cand = root.resolve(path.startsWith("/") ? path.substring(1) : path).normalize();
      if (Files.isRegularFile(cand)) { file = cand; break; }
    }
    if (file == null && "/index.html".equals(path)) {
      for (Path root : new Path[]{Paths.get("Frontend"), Paths.get(".")}) {
        Path cand = root.resolve("chat.html");
        if (Files.isRegularFile(cand)) { file = cand; break; }
      }
    }
    if (file == null) {
      byte[] b = ("<!DOCTYPE html><html><body style='font-family:sans-serif;padding:2rem'>"
        + "<h1>Infinity AI 1</h1><p>API: <a href='/api/health'>/api/health</a></p></body></html>")
        .getBytes(StandardCharsets.UTF_8);
      writeBytes(ex, 200, "text/html; charset=utf-8", b);
      return;
    }
    String name = file.getFileName().toString().toLowerCase();
    String ctype = "application/octet-stream";
    if (name.endsWith(".html")) ctype = "text/html; charset=utf-8";
    else if (name.endsWith(".js")) ctype = "application/javascript; charset=utf-8";
    else if (name.endsWith(".css")) ctype = "text/css; charset=utf-8";
    else if (name.endsWith(".json")) ctype = "application/json; charset=utf-8";
    else if (name.endsWith(".png")) ctype = "image/png";
    else if (name.endsWith(".svg")) ctype = "image/svg+xml";
    byte[] data = Files.readAllBytes(file);
    ex.getResponseHeaders().add("Cache-Control", name.endsWith(".html") ? "no-cache" : "public, max-age=86400");
    writeBytes(ex, 200, ctype, data);
  }

  /** Safe for GET and HEAD (Render health checks use HEAD). */
  static void writeBytes(HttpExchange ex, int code, String contentType, byte[] data) throws IOException {
    ex.getResponseHeaders().set("Content-Type", contentType);
    if (isHead(ex)) {
      ex.sendResponseHeaders(code, -1);
      ex.getResponseBody().close();
      return;
    }
    ex.sendResponseHeaders(code, data.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(data);
    }
  }

  public static void json(HttpExchange ex, int code, String body) throws IOException {
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    if (isHead(ex)) {
      ex.sendResponseHeaders(code, -1);
      ex.getResponseBody().close();
      return;
    }
    ex.sendResponseHeaders(code, b.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(b);
    }
  }
}

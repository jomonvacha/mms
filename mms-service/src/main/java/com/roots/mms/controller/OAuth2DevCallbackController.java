package com.roots.mms.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public class OAuth2DevCallbackController {

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  @GetMapping(value = "/callback-dev", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> callback(@RequestParam(required = false) String token,
                                         @RequestParam(required = false) String refreshToken) {
    String body = """
      <!doctype html>
      <html lang=\"en\">
      <head>
        <meta charset=\"utf-8\" />
        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
        <title>OAuth2 Tokens (DEV)</title>
        <style>
          body { font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; margin: 2rem; }
          code, textarea { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
          .box { border: 1px solid #ddd; padding: 1rem; border-radius: 8px; }
          .row { margin-bottom: 1rem; }
          textarea { width: 100%%; height: 6rem; }
          button { padding: 0.5rem 1rem; margin-right: 0.5rem; }
          .warn { color: #b45309; }
        </style>
      </head>
      <body>
        <h1>OAuth2 Tokens (DEV)</h1>
        <p class=\"warn\">For development only. Do not deploy this page to production.</p>
        <div class=\"box\">
          <div class=\"row\">
            <label>Access Token</label>
            <textarea id=\"token\">%s</textarea>
            <button onclick=\"copy('token')\">Copy Access Token</button>
          </div>
          <div class=\"row\">
            <label>Refresh Token</label>
            <textarea id=\"refresh\">%s</textarea>
            <button onclick=\"copy('refresh')\">Copy Refresh Token</button>
          </div>
          <div class=\"row\">
            <button onclick=\"toMe()\">Call /api/users/me</button>
            <pre id=\"result\"></pre>
          </div>
        </div>
        <script>
          function copy(id){ const el = document.getElementById(id); el.select(); document.execCommand('copy'); }
          async function toMe(){
            const t = document.getElementById('token').value.trim();
            const res = await fetch('/api/users/me', { headers: { 'Authorization': 'Bearer ' + t } });
            const text = await res.text();
            document.getElementById('result').textContent = text;
          }
        </script>
      </body>
      </html>
      """.formatted(nvl(token), nvl(refreshToken));
    return ResponseEntity.ok(body);
  }
}

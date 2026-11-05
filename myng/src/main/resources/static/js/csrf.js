/*
 * Автоматически добавляет CSRF-токен Spring Security во все изменяющие
 * запросы fetch (POST, PUT, DELETE, PATCH) к нашему же сайту.
 *
 * Токен и имя заголовка берутся из мета-тегов в layout.html:
 *   <meta name="_csrf" ...>  и  <meta name="_csrf_header" ...>
 *
 * Благодаря этому не нужно вручную передавать токен в каждом вызове fetch.
 */
(function () {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const headerName = document.querySelector('meta[name="_csrf_header"]')?.content;

  if (!token || !headerName) {
    return;
  }

  const SAFE_METHODS = ["GET", "HEAD", "OPTIONS", "TRACE"];
  const originalFetch = window.fetch;

  window.fetch = function (input, init = {}) {
    const request = input instanceof Request ? input : null;
    const method = (init.method || request?.method || "GET").toUpperCase();
    const url = new URL(request ? request.url : input, window.location.href);

    // токен отправляем только на свой сайт и только в изменяющих запросах
    if (!SAFE_METHODS.includes(method) && url.origin === window.location.origin) {
      const headers = new Headers(init.headers || request?.headers);

      if (!headers.has(headerName)) {
        headers.set(headerName, token);
      }

      init = { ...init, headers };
    }

    return originalFetch.call(this, input, init);
  };
})();

const CACHE_NAME = "guardian-admin-shell-v1";
self.addEventListener("install", event => { event.waitUntil(self.skipWaiting()); });
self.addEventListener("activate", event => { event.waitUntil((async () => { for (const key of await caches.keys()) if (key.startsWith("guardian-admin-")) await caches.delete(key); await self.clients.claim(); })()); });
// Admin data and credentials are never cached; use the network only.
self.addEventListener("fetch", event => { if (event.request.method === "GET") event.respondWith(fetch(event.request, { cache: "no-store" })); });

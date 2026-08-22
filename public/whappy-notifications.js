self.addEventListener("push", (event) => {
  const data = event.data?.json?.() || {};
  const title = data.title || "Whappy";
  const options = { body: data.body || "Vous avez une nouvelle activité.", icon: "/wapi-logo.svg", badge: "/wapi-logo.svg", data: data.url || "/" };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clients) => {
    const target = event.notification.data || "/";
    const existing = clients.find((client) => "focus" in client);
    return existing ? existing.focus() : self.clients.openWindow(target);
  }));
});
